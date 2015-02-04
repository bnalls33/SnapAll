package com.brandonnalls.snapall;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findConstructorExact;
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
    Created for proguarded Snapchat 8.1.0
    Adds a "SelectAll" checkbox to the top of the Friend Selection Fragment.
    Lets you send snaps to all your friends ASAP.

    This code is disgusting. I didn't use generics, and more, since we can't have
    type safety (this isn't compiled with snapchat code). This is a hack.
 */
public class Snapall implements IXposedHookLoadPackage {
    public static final String groupButtonContainerName = "sharedButtonContainer"; //This contains SnapAll + SnapGroups
    private static final XSharedPreferences PREFS = new XSharedPreferences("com.brandonnalls.snapall");

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.snapchat.android"))
            return;

        //method h basically does findViewByIds (via SnapChatFragment's findviewbyid wrapper
        // passing in button ids from R.java) then sets onclicklisteners
        findAndHookMethod("com.snapchat.android.fragments.sendto.SendToFragment", lpparam.classLoader, "h", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                try {
                    final Context c = (Context) callMethod(param.thisObject, "getActivity");

                    //Otherbutton (Var b) is from R.java send_to_action_bar_search_button = 2131362379
                    ///   reverse looked that # up in method SendToFragment "h" where it findsViewById(2131362379) via alias method.
                    View otherButton = (View) getObjectField(param.thisObject, "b");

                    //Creates a container for SnapAll and SnapGroup XPosed mod buttons (if it doesn't exist already)
                    // and puts this snapGroup button into the container, next to "otherbutton" (preexisting snapchat button)
                    LinearLayout sharedButtonContainer = (LinearLayout) getAdditionalInstanceField(param.thisObject, groupButtonContainerName);
                    if(sharedButtonContainer == null || !(sharedButtonContainer instanceof LinearLayout)) {
                        RelativeLayout.LayoutParams myParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        myParams.setMargins(0, 0, 0, 0);
                        myParams.addRule(RelativeLayout.LEFT_OF, otherButton.getId());
                        myParams.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
                        sharedButtonContainer = new LinearLayout(c);
                        sharedButtonContainer.setOrientation(LinearLayout.HORIZONTAL);
                        sharedButtonContainer.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
                        ((RelativeLayout)otherButton.getParent()).addView(sharedButtonContainer, myParams);
                        setAdditionalInstanceField(param.thisObject, groupButtonContainerName, sharedButtonContainer);
                    }
                    CheckBox selectAll = getCheckbox(c);
                    LinearLayout.LayoutParams myParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    myParams.setMargins(0,0,30,0);
                    sharedButtonContainer.addView(selectAll, myParams);
                    selectAll.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton compoundButton, boolean addFriends) {
                            PREFS.reload();
                            final boolean checkStoryToo = PREFS.getBoolean("select_my_story", true);

                            //SendToAdapter : var d is the only SendToAdpater in SendToFragment
                            Object hopefullyArrayAdapter =  getObjectField(param.thisObject, "d");

                            if (hopefullyArrayAdapter != null && hopefullyArrayAdapter instanceof ArrayAdapter) {
                                ArrayAdapter aa = (ArrayAdapter) hopefullyArrayAdapter;

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

                                    int numUsersAdded = 0;
                                    Class<?>[] types = getParameterTypes(friendAndStoryList.toArray());
                                    for (int i = 0; i < types.length; i++) {
                                        Object thingToAdd = friendAndStoryList.get(i);
                                        if (types[i].getCanonicalName().equals("com.snapchat.android.model.Friend")) {
                                            if (addFriends) {
                                                destinationFriendSet.add(thingToAdd);
                                                numUsersAdded++;
                                                if(numUsersAdded >= 199) {
                                                    // Server enforces 200 recipient limit. Not sure if 200 includes the story.
                                                    Toast.makeText(c, "You have too many friends. Install SnapGroups instead.", Toast.LENGTH_LONG).show();
                                                    break;
                                                }
                                            } else
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
                                    callMethod(param.thisObject, "b");
                                } catch (Throwable t) {
                                    XposedBridge.log("Snapall failed to check all. Check snapchat version compatibility.");
                                    XposedBridge.log(t.toString());
                                }

                            }
                        }
                    });
                } catch (Throwable t) {
                    XposedBridge.log("Snapall failed to insert checkbox.");
                    return;
                }


            }
        });

        /**
         * These hide the checkbox while the search box is displayed
         */
        findAndHookMethod("com.snapchat.android.fragments.sendto.SendToFragment", lpparam.classLoader, "o", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View v = (View) getAdditionalInstanceField(param.thisObject, groupButtonContainerName);
                v.setVisibility(View.VISIBLE);
            }
        });

        findAndHookMethod("com.snapchat.android.fragments.sendto.SendToFragment", lpparam.classLoader, "p", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View v = (View) getAdditionalInstanceField(param.thisObject, groupButtonContainerName);
                v.setVisibility(View.INVISIBLE);
            }
        });
    }

    /**
     * Opens SnapChat's Resources and gets the pretty checkbox, for reuse & consistent appearance
     * @param c SNAPCHAT's context
     * @return A pretty checkbox (hopefully)
     */
    public static CheckBox getCheckbox(Context c) {
        CheckBox cb = new CheckBox(c);
        try {
            //Setting properties from snapchat's res/layout/send_to_item.xml checkbox
            cb.setButtonDrawable(c.getResources().getIdentifier("send_to_button_selector", "drawable", "com.snapchat.android"));
            //May need to scale drawable bitmap...
            cb.setScaleX(0.7F);
            cb.setScaleY(0.7F);
        } catch (Exception e) {
            XposedBridge.log("SnapAll: Error creating fancy checkbox");
        }
        return cb;
    }

}