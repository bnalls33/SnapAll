package com.brandonnalls.snapall;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getParameterTypes;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers.*; //Here for typeahead
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;


/*
    Created for proguarded Snapchat 8.0.0
    Adds a "SelectAll" checkbox to the top of the Friend Selection Fragment.
    Lets you send snaps to all your friends ASAP.

    This code is disgusting. I didn't use generics, and more, since we can't have
    type safety (this isn't compiled with snapchat code). This is a hack.
 */
public class Snapall implements IXposedHookLoadPackage {
    public static final String checkBoxName = "selectAllCheckBoxAdditionalField";
    private final static XSharedPreferences PREFS = new XSharedPreferences("com.brandonnalls.snapall");

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.snapchat.android"))
            return;

        //method h basically does findViewByIds (via SnapChatFragment's findviewbyid wrapper
        // passing in button ids from R.java) then sets onclicklisteners
        findAndHookMethod("com.snapchat.android.fragments.sendto.SendToFragment", lpparam.classLoader, "h", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                //Var b is from R.java send_to_action_bar_search_button = 2131427800
                ///   reverse looked that # up in method "h" where it findsViewById.
                CheckBox selectAll;
                try {
                    View otherButton = (View) getObjectField(param.thisObject, "b");
                    Context c = (Context) callMethod(param.thisObject, "getActivity");
                    selectAll = new CheckBox(c);
                    RelativeLayout.LayoutParams myParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
                    myParams.addRule(RelativeLayout.LEFT_OF, otherButton.getId());
                    ((RelativeLayout) otherButton.getParent()).addView(selectAll, myParams);
                    setAdditionalInstanceField(param.thisObject, checkBoxName, selectAll);
                } catch (Throwable t) {
                    XposedBridge.log("Snapall failed to insert checkbox.");
                    return;
                }

                selectAll.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean addFriends) {
                        PREFS.reload();
                        final boolean checkStoryToo = PREFS.getBoolean("select_my_story", true);

                        //SendToAdapter : var d is the only SendToAdpater in SendToFragment
                        Object hopefullyArrayAdapter =  getObjectField(param.thisObject, "d");

                        if (hopefullyArrayAdapter != null && hopefullyArrayAdapter instanceof ArrayAdapter) {
                            ArrayAdapter aa = (ArrayAdapter) hopefullyArrayAdapter;
                            //TODO: notify if >200 recipients. UI used to block it, might be enforced server side.

                            /* Easier, slower method here. Uses the callback function which is slow
                            //Not sure which arraylist to use, d or e.... seem like nearly duplicates
                            List sendToItemList = (List) getObjectField(aa, "d");
                            Object sendToCheckedCallback = getObjectField(aa, "i");
                            for (Object sendToItem : sendToItemList) {
                                if (sendToCheckedCallback != null)
                                    callMethod(sendToCheckedCallback, "a", sendToItem, b);
                            }*/ // This works

                            //Not sure which arraylist to use, d or e.... seem like nearly duplicates
                            ArrayList friendAndStoryList;
                            Set destinationFriendSet;
                            List destinationStoryList;

                            try {
                                //From SendtoAdapter... there are two lists, just guessed....
                                friendAndStoryList = (ArrayList) getObjectField(aa, "d");

                                //From SendtoFragment.. the two collections, one is a list, one set
                                destinationFriendSet = (Set) getObjectField(param.thisObject, "l");
                                destinationStoryList = (List) getObjectField(param.thisObject, "m");

                                Class<?>[] types = getParameterTypes(friendAndStoryList.toArray());
                                for (int i = 0; i < types.length; i++) {
                                    Object thingToAdd = friendAndStoryList.get(i);
                                    if (types[i].getCanonicalName().equals("com.snapchat.android.model.Friend")) {
                                        if (addFriends)
                                            destinationFriendSet.add(thingToAdd);
                                        else
                                            destinationFriendSet.remove(thingToAdd);
                                    } else if (types[i].getCanonicalName().equals("com.snapchat.android.model.MyPostToStory")) {
                                        if(checkStoryToo) {
                                            if (addFriends)
                                                destinationStoryList.add(thingToAdd);
                                            else
                                                destinationStoryList.remove(thingToAdd);
                                        }
                                    } else {
                                        XposedBridge.log("Snappall: Found unknown type: " + types[i].toString());
                                    }
                                }

                                // Method in SendToFragment that will have the UI match the data
                                // source by putting ", " between friends' names in the bottom blue
                                // bar. It has iterators (localInterator1) and such.
                                callMethod(param.thisObject, "d");
                            } catch (Throwable t) {
                                XposedBridge.log("Snapall failed to check all. Check snapchat version compatibility.");
                                XposedBridge.log(t.toString());
                            }

                        }
                    }
                });
            }
        });

        /**
         * These hide the checkbox while the search box is displayed
         */
        findAndHookMethod("com.snapchat.android.fragments.sendto.SendToFragment", lpparam.classLoader, "m", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View v = (View) getAdditionalInstanceField(param.thisObject, checkBoxName);
                v.setVisibility(View.VISIBLE);
            }
        });

        findAndHookMethod("com.snapchat.android.fragments.sendto.SendToFragment", lpparam.classLoader, "n", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View v = (View) getAdditionalInstanceField(param.thisObject, checkBoxName);
                v.setVisibility(View.INVISIBLE);
            }
        });
    }

}