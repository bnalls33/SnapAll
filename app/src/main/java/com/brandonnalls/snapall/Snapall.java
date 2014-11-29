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
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers.*;
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

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.snapchat.android"))
            return;

        //method h basically does findViewByIds (via SnapChatFragment's findviewbyid wrapper
        // passing in button ids from R.java) then sets onclicklisteners
        findAndHookMethod("com.snapchat.android.fragments.sendto.SendToFragment", lpparam.classLoader, "h", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                //Var b is from R.java send_to_action_bar_friend_button = 2131362248.
                ///   reverse looked that # up in method "h" where it findsViewById.
                View otherButton = (View) getObjectField(param.thisObject, "b");
                Context c = (Context) callMethod(param.thisObject, "getActivity");
                CheckBox selectAll = new CheckBox(c);
                RelativeLayout.LayoutParams myParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
                myParams.addRule(RelativeLayout.LEFT_OF, otherButton.getId());
                ((RelativeLayout) otherButton.getParent()).addView(selectAll, myParams);
                setAdditionalInstanceField(param.thisObject, checkBoxName, selectAll);


                selectAll.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                        //SendToAdapter
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
                                friendAndStoryList = (ArrayList) getObjectField(aa, "d");
                                destinationFriendSet = (Set) getObjectField(param.thisObject, "l");
                                destinationStoryList = (List) getObjectField(param.thisObject, "m");
                                Class<?>[] types = getParameterTypes(friendAndStoryList.toArray());
                                for (int i = 0; i < types.length; i++) {
                                    Object thingToAdd = friendAndStoryList.get(i);
                                    if (types[i].getCanonicalName().equals("com.snapchat.android.model.Friend")) {
                                        if (b)
                                            destinationFriendSet.add(thingToAdd);
                                        else
                                            destinationFriendSet.remove(thingToAdd);
                                    } else if (types[i].getCanonicalName().equals("com.snapchat.android.model.MyPostToStory")) {
                                        if (b)
                                            destinationStoryList.add(thingToAdd);
                                        else
                                            destinationStoryList.remove(thingToAdd);
                                    } else {
                                        XposedBridge.log("Snappall: Found unknown type: " + types[i].toString());
                                    }
                                }
                                callMethod(param.thisObject, "e");
                            } catch (Throwable t) {
                                XposedBridge.log("Snapall failed to check all. Check snapchat version compatibility.");
                            }

                        }
                    }
                });
            }
        });

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