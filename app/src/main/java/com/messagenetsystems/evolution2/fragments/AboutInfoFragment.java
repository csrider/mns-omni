package com.messagenetsystems.evolution2.fragments;

/** AboutInfoFragment
 * Fragment for showing the about-info dialog.
 *
 * Example to use this in an Activity...
 *  FragmentManager fm = getSupportFragmentManager();
 *  AboutInfoFragment aboutInfoFragment = AboutInfoFragment.newInstance(AboutInfoFragment.DEFAULT_TITLE);
 *  aboutInfoFragment.show(fm, "fragment_about_info");
 *
 * Revisions:
 *  2020.03.02      Chris Rider     Created.
 */

//import android.app.DialogFragment;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.support.v4.app.DialogFragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.messagenetsystems.evolution2.R;
import com.messagenetsystems.evolution2.models.Release;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AboutInfoFragment extends DialogFragment {
    private final String TAG = this.getClass().getSimpleName();

    public static final String KEYNAME_TITLE = "title";
    public static String DEFAULT_TITLE = "About This Omni";

    public static final String KEYNAME_VERSION = "version";

    private TextView subtitleOmniInfoTextView;
    private TextView omniInfoTextView;
    private TextView subtitleReleaseTextView;
    private TextView versionTextView;
    private TextView dateTextView;
    private TextView componentsTextView;

    Release release = null;
    Release.Component component = null;
    List<Release> releases = new ArrayList<>();
    List<Release.Component> components = new ArrayList<>();

    /** Constructor */
    public AboutInfoFragment() {
        // Empty constructor is required for DialogFragment
        // Make sure not to add arguments to the constructor
        // Use 'newInstance' instead as shown below
    }

    /** Method to return an instance of this fragment.
     * This is also where you should provide any arguments. */
    public static AboutInfoFragment newInstance(String title, String versionToLookup) {
        // Create an instance of this fragment to ultimately return
        AboutInfoFragment aboutInfoFragment = new AboutInfoFragment();

        // Create a bundle for arguments that we can add to the fragment object
        Bundle args = new Bundle();

        // Set various arguments to pass along...
        // (you can later retrieve these arguments in onViewCreated below)
        args.putString(KEYNAME_TITLE, title);                   //set the provided title
        args.putString(KEYNAME_VERSION, versionToLookup);       //set the provided version to lookup

        // Add the argument bundle to the fragment and return it
        aboutInfoFragment.setArguments(args);
        return aboutInfoFragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_about_info, container);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String versionToLookup;

        // Get field from view
        subtitleOmniInfoTextView = (TextView) view.findViewById(R.id.fragAboutInfo_subtitleOmniInfoTextView);
        omniInfoTextView = (TextView) view.findViewById(R.id.fragAboutInfo_omniInfoTextView);
        subtitleReleaseTextView = (TextView) view.findViewById(R.id.fragAboutInfo_subtitleReleaseTextView);
        versionTextView = (TextView) view.findViewById(R.id.fragAboutInfo_versionTextView);
        dateTextView = (TextView) view.findViewById(R.id.fragAboutInfo_dateTextView);
        componentsTextView = (TextView) view.findViewById(R.id.fragAboutInfo_componentsTextView);

        // Fetch any arguments from bundle that got set into the fragment instance and set/use as desired
        String title = getArguments().getString(KEYNAME_TITLE, DEFAULT_TITLE);
        getDialog().setTitle(title);
        versionToLookup = getArguments().getString(KEYNAME_VERSION, null);

        try {
            subtitleOmniInfoTextView.setText("Omni Information");

            omniInfoTextView.setText(generateOmniInfo());
        } catch (Exception e) {
            Log.e(TAG, "onViewCreated: Exception caught preparing Omni info: "+e.getMessage());
            omniInfoTextView.setText("(not available)");
        }

        try {
            String releaseNotes = generateReleaseNotes(versionToLookup);    //need to do this before getRelease() call
            Release currentRelease = getRelease(versionToLookup);           //requires call to generateReleaseNotes() first
            StringBuilder components = new StringBuilder();

            //mainTextView.setText(generateReleaseNotes(versionToLookup));
            //TODO: formatted better

            subtitleReleaseTextView.setText("Release Notes");

            versionTextView.setText("Version: "+currentRelease.getVersion());
            dateTextView.setText("Released on: "+currentRelease.getDate());

            String bullet = "\u2022 ";
            for (Release.Component c : currentRelease.getComponents()) {
                components.append(bullet).append(c.getType()).append(" - ");
                components.append(c.getSummary()).append("\n");
            }
            componentsTextView.setText(components.toString());
        } catch (Exception e) {
            Log.e(TAG, "onViewCreated: Exception caught preparing release info: "+e.getMessage());
        }
    }

    public String generateOmniInfo() {
        StringBuilder omniInfo = new StringBuilder();

        //device ID

        //serial number
        //IP & MAC addresses
        //uptime

        return omniInfo.toString();
    }

    /** Reads the release_notes.xml file and generates data for Releases class. *
     * You can also optionally use the returned value as just a string value.
     * @param versionToLookup
     * @return String containing release info.
     * @throws XmlPullParserException
     * @throws IOException
     */
    public String generateReleaseNotes(String versionToLookup) throws XmlPullParserException, IOException {
        /* EXAMPLE....
        <ReleaseNotes>

        <Release>
            <Date>2020.02.27</Date>
            <Version>2.1.0</Version>
            <Author>Chris Rider</Author>
            <Component type="New" author="Chris Rider">
                <Summary>Very first initial test release.</Summary>
                <Description>The very first release to Kevin for initial testing. Functionality is simply same-priority scrolling message launch and delivery order / rotation.</Description>
            </Component>
        </Release>

        </ReleaseNotes>
         */

        StringBuilder releaseInfo = new StringBuilder().append("Release Notes\n");
        Resources res = getActivity().getResources();
        XmlResourceParser xrp = res.getXml(R.xml.release_notes);

        // First, just parse the XML into model-objects and lists...
        int eventType = xrp.next();
        while (eventType != XmlPullParser.END_DOCUMENT) {

            ////////////////////
            // <Release>
            if(eventType == XmlPullParser.START_TAG && xrp.getName().equals("Release")) {
                //encountered a starting <Release> tag
                Log.v(TAG, "<Release> encountered, creating a Release instance.");
                release = new Release();
                components = new ArrayList<>();

                //move to next node and start looping through child nodes of <Release> until we reach the "Release" end tag
                eventType = xrp.next();
                while (!(eventType == XmlPullParser.END_TAG && xrp.getName().equals("Release"))) {
                    //looping until we reach ending </Release> tag....

                    if (eventType == XmlPullParser.START_TAG && xrp.getName().equals("Date")) {
                        xrp.next(); //go to next node (the actual text wrapped by the tag)
                        Log.v(TAG, "<Date> = \""+String.valueOf(xrp.getText())+"\"");
                        release.setDate(xrp.getText());
                    } else if (eventType == XmlPullParser.START_TAG && xrp.getName().equals("Version")) {
                        xrp.next(); //go to next node (the actual text wrapped by the tag)
                        Log.v(TAG, "<Version> = \""+String.valueOf(xrp.getText())+"\"");
                        release.setVersion(xrp.getText());
                    } else if (eventType == XmlPullParser.START_TAG && xrp.getName().equals("Author")) {
                        xrp.next(); //go to next node (the actual text wrapped by the tag)
                        Log.v(TAG, "<Author> = \""+String.valueOf(xrp.getText())+"\"");
                        release.setAuthor(xrp.getText());
                    } else if (eventType == XmlPullParser.START_TAG && xrp.getName().equals("Component")) {
                        //we have encountered a component (an individual release note -- there may be more than one)
                        Log.v(TAG, "<Component> encountered, creating a Release instance.");
                        component = new Release.Component();

                        //parse attributes of <Component>
                        component.setType((xrp.getAttributeValue(null, "type")));
                        component.setAuthor((xrp.getAttributeValue(null, "author")));

                        //parse children of <Component>
                        eventType = xrp.next();
                        while (!(eventType == XmlPullParser.END_TAG && xrp.getName().equals("Component"))) {
                            //looping until we reach ending </Component> tag....

                            if (eventType == XmlPullParser.START_TAG && xrp.getName().equals("Summary")) {
                                xrp.next(); //advance to text node in this tag
                                component.setSummary(xrp.getText());
                            } else if (eventType == XmlPullParser.START_TAG && xrp.getName().equals("Description")) {
                                xrp.next(); //advance to text node in this tag
                                component.setDescription(xrp.getText());
                            }

                            eventType = xrp.next();
                        }//end while not </Component>

                        //add the completed component to the list
                        components.add(new Release.Component(component));
                    }

                    eventType = xrp.next(); //move to next child tag inside "Release"
                }//end while not </Release>
            }//end if starting <Release> tag

            ////////////////////
            // </Release>
            if(eventType == XmlPullParser.END_TAG && xrp.getName().equals("Release")) {
                //add completed components list to the release
                if (release != null) {
                    release.setComponents(components);
                }

                //close out the Release model object and add it to the list
                if (release != null) {
                    releases.add(new Release(release));
                }
            }//end if ending </Release> tag

            eventType = xrp.next();
        }//end while not end of document

        // Now we can generate text from our orderly objects/lists...
        for (Release r : releases) {
            if (r.getVersion().equals(versionToLookup)) {
                releaseInfo.append("Version: ").append(r.getVersion()).append("\n");
                releaseInfo.append("Released: ").append(r.getDate()).append("\n");
                for (Release.Component c : r.getComponents()) {
                    releaseInfo.append(c.getType()).append(": ").append(c.getSummary()).append("\n");
                    //releaseInfo.append("  (").append(c.getDescription()).append(")\n");
                }
                break;
            }
        }

        Log.v(TAG, "Returning:\n"+releaseInfo.toString());
        return releaseInfo.toString();
    }

    private Release getRelease(String versionToLookup) {
        Release ret = null;
        for (Release r : releases) {
            if (r.getVersion().equals(versionToLookup)) {
                ret = r;
                break;
            }
        }
        return ret;
    }
}
