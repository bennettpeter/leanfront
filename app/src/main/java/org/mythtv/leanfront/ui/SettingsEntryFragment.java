/*
 * Copyright (c) 2019-2020 Peter Bennett
 *
 * This file is part of MythTV-leanfront.
 *
 * MythTV-leanfront is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * MythTV-leanfront is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with MythTV-leanfront.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.mythtv.leanfront.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.model.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SettingsEntryFragment extends GuidedStepSupportFragment {

    private static final int ID_BACKEND_IP = 1;
    private static final int ID_HTTP_PORT = 2;
    private static final int ID_BOOKMARK_LOCAL = 5;
    private static final int ID_PLAYBACK = 7;
    private static final int ID_SKIP_FWD = 8;
    private static final int ID_SKIP_BACK = 9;
    private static final int ID_JUMP = 10;
    private static final int ID_PROG_LIST_OPTIONS = 11;
    private static final int ID_SORT_ORIG_AIRDATE = 12;
    private static final int ID_DESCENDING = 13;
    private static final int ID_BACKEND_MAC = 14;
    private static final int ID_BACKEND = 15;
    private static final int ID_AUDIO = 16;
    private static final int ID_AUDIO_AUTO = 17;
    private static final int ID_AUDIO_MEDIACODEC = 18;
    private static final int ID_AUDIO_FFMPEG = 19;
    private static final int ID_ARROW_JUMP = 20;
    private static final int ID_LIVETV_DURATION = 21;
    private static final int ID_FRAMERATE_MATCH = 22;
    private static final int ID_SUBTITLE_SIZE = 23;
    private static final int ID_ERROR_TOAST = 24;
    private static final int ID_SHOW_RECENTS = 25;
    private static final int ID_RECENTS_DELETED = 26;
    private static final int ID_RECENTS_WATCHED = 27;
    private static final int ID_RELATED_DELETED = 28;
    private static final int ID_RELATED_WATCHED = 29;
    private static final int ID_RECENTS_DAYS = 30;
    private static final int ID_LETTERBOX_BLACK = 30;
    private static final int ID_RECENTS_TRIM = 31;
    private static final int ID_TWEAKS = 32;
    private static final int ID_TWEAK_SEARCH_PKTS = 33;

    private static final String KEY_EXPAND = "EXPAND";

    private SharedPreferences.Editor mEditor;

    private GuidedAction mBackendAction;
    private GuidedAction mAudioAction;

    private String mPriorBackend;
    private String mPriorHttpPort;

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        Activity activity = getActivity();
        String title = getString(R.string.personal_settings);
        String breadcrumb = "";
        String description = getString(R.string.pref_title_settings);
        Drawable icon = activity.getDrawable(R.drawable.ic_settings);
        return new GuidanceStylist.Guidance(title, description, breadcrumb, icon);
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        mEditor = Settings.getEditor();

        List<GuidedAction> subActions = new ArrayList<>();
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_BACKEND_IP)
                .title(R.string.pref_title_backend_ip)
                .description(Settings.getString("pref_backend"))
                .descriptionEditable(true)
                .build());
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_HTTP_PORT)
                .title(R.string.pref_title_http_port)
                .description(Settings.getString("pref_http_port"))
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER)
                .build());
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_BACKEND_MAC)
                .title(R.string.pref_title_backend_mac)
                .description(Settings.getString("pref_backend_mac"))
                .descriptionEditable(true)
                .build());
        actions.add(mBackendAction = new GuidedAction.Builder(getActivity())
                .id(ID_BACKEND)
                .title(R.string.pref_title_backend)
                .description(backendDesc())
                .subActions(subActions)
                .build());

        subActions = new ArrayList<>();
        String str = Settings.getString("pref_framerate_match");
        GuidedAction.Builder tmp = new GuidedAction.Builder(getActivity());
        tmp     .id(ID_FRAMERATE_MATCH)
                .title(R.string.pref_title_framerate_match)
                .checked("true".equals(str))
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID);
        if (android.os.Build.VERSION.SDK_INT < 23)
            tmp .description(R.string.pref_msg_needs_6_0)
                .enabled(false);
        subActions.add(tmp.build());
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_SKIP_FWD)
                .title(R.string.pref_title_skip_fwd)
                .description(Settings.getString("pref_skip_fwd"))
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER)
                .build());
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_SKIP_BACK)
                .title(R.string.pref_title_skip_back)
                .description(Settings.getString("pref_skip_back"))
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER)
                .build());
        str = Settings.getString("pref_arrow_jump");
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_ARROW_JUMP)
                .title(R.string.pref_title_arrow_jump)
                .checked("true".equals(str))
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .build());
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_JUMP)
                .title(R.string.pref_title_jump)
                .description(Settings.getString("pref_jump"))
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER)
                .build());
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_LIVETV_DURATION)
                .title(R.string.pref_title_livetv_duration)
                .description(Settings.getString("pref_livetv_duration"))
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER)
                .build());
        str = Settings.getString("pref_bookmark");
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_BOOKMARK_LOCAL)
                .title(R.string.pref_bookmark_local)
                .checked("local".equals(str))
                .description(R.string.pref_bookmark_mythtv)
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .build());
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_SUBTITLE_SIZE)
                .title(R.string.pref_title_subtitle_size)
                .description(Settings.getString("pref_subtitle_size"))
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER)
                .build());
        str = Settings.getString("pref_error_toast");
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_ERROR_TOAST)
                .title(R.string.pref_error_toast)
                .checked("true".equals(str))
                .description(R.string.pref_error_toast_desc)
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .build());
        int i = Settings.getInt("pref_letterbox_color");
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_LETTERBOX_BLACK)
                .title(R.string.pref_letterbox_black)
                .checked(i == Color.BLACK)
                .description(R.string.pref_letterbox_black_desc)
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .build());
        actions.add(new GuidedAction.Builder(getActivity())
                .id(ID_PLAYBACK)
                .title(R.string.pref_title_playback)
                .subActions(subActions)
                .build());

        subActions = new ArrayList<>();
        str = Settings.getString("pref_seq");
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_SORT_ORIG_AIRDATE)
                .title(R.string.pref_seq_orig_airdate)
                .checked("airdate".equals(str))
                .description(R.string.pref_seq_orig_airdate_desc)
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .build());
        str = Settings.getString("pref_seq_ascdesc");
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_DESCENDING)
                .title(R.string.pref_seq_descending)
                .checked("desc".equals(str))
                .description(R.string.pref_seq_descending_desc)
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .build());
        String recents = Settings.getString("pref_show_recents");
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_SHOW_RECENTS)
                .title(R.string.pref_show_recents)
                .checked("true".equals(recents))
                .description(R.string.pref_show_recents_desc)
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .build());
        str = Settings.getString("pref_recents_days");
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_RECENTS_DAYS)
                .title(R.string.pref_recents_days)
                .description(Settings.getString("pref_recents_days"))
                .descriptionEditable("true".equals(recents))
                .enabled("true".equals(recents))
                .focusable("true".equals(recents))
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER)
                .build());
        String recentsDel = Settings.getString("pref_recents_deleted");
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_RECENTS_DELETED)
                .title(R.string.pref_recents_deleted)
                .description(R.string.pref_recents_deleted_desc)
                .checked("true".equals(recentsDel))
                .enabled("true".equals(recents))
                .focusable("true".equals(recents))
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .build());
        String recentsWatched = Settings.getString("pref_recents_watched");
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_RECENTS_WATCHED)
                .title(R.string.pref_recents_watched)
                .description(R.string.pref_recents_watched_desc)
                .checked("true".equals(recentsWatched))
                .enabled("true".equals(recents))
                .focusable("true".equals(recents))
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .build());
        str = Settings.getString("pref_recents_trim");
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_RECENTS_TRIM)
                .title(R.string.pref_recents_trim)
                .description(R.string.pref_recents_trim_desc)
                .checked("true".equals(str))
                .enabled("true".equals(recents)
                        && ("true".equals(recentsDel) || "true".equals(recentsWatched)))
                .focusable("true".equals(recents)
                        && ("true".equals(recentsDel) || "true".equals(recentsWatched)))
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .build());
        str = Settings.getString("pref_related_deleted");
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_RELATED_DELETED)
                .title(R.string.pref_related_deleted)
                .checked("true".equals(str))
                .description(R.string.pref_related_deleted_desc)
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .build());
        str = Settings.getString("pref_related_watched");
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_RELATED_WATCHED)
                .title(R.string.pref_related_watched)
                .checked("true".equals(str))
                .description(R.string.pref_related_watched_desc)
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .build());
        actions.add(new GuidedAction.Builder(getActivity())
                .id(ID_PROG_LIST_OPTIONS)
                .title(R.string.pref_title_lists)
                .subActions(subActions)
                .build());

        subActions = new ArrayList<GuidedAction>();
        String audio = Settings.getString("pref_audio");
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_AUDIO_AUTO)
                .title(R.string.pref_audio_auto)
                .checked("auto".equals(audio))
                .description(R.string.pref_audio_auto_desc)
                .checkSetId(ID_AUDIO)
                .build());
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_AUDIO_MEDIACODEC)
                .title(R.string.pref_audio_mediacodec)
                .checked("mediacodec".equals(audio))
                .description(R.string.pref_audio_mediacodec_desc)
                .checkSetId(ID_AUDIO)
                .build());
        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_AUDIO_FFMPEG)
                .title(R.string.pref_audio_ffmpeg)
                .checked("ffmpeg".equals(audio))
                .description(R.string.pref_audio_ffmpeg_desc)
                .checkSetId(ID_AUDIO)
                .build());
        actions.add(mAudioAction = new GuidedAction.Builder(getActivity())
                .id(ID_AUDIO)
                .title(R.string.pref_ff_title)
                .description(audiodesc())
                .subActions(subActions)
                .build());

        subActions = new ArrayList<GuidedAction>();

        subActions.add(new GuidedAction.Builder(getActivity())
                .id(ID_TWEAK_SEARCH_PKTS)
                .title(R.string.pref_tweak_ts_search_pkts)
                .description(Settings.getString("pref_tweak_ts_search_pkts"))
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER)
                .build());
        actions.add(mAudioAction = new GuidedAction.Builder(getActivity())
                .id(ID_TWEAKS)
                .title(R.string.pref_tweaks_title)
                .description(R.string.pref_tweaks_desc)
                .subActions(subActions)
                .build());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View ret =  super.onCreateView(inflater, container, savedInstanceState);
        int expandId = getActivity().getIntent()
                .getIntExtra(KEY_EXPAND, 0);
        GuidedAction action = findActionById(expandId);
        if (action != null)
            expandAction(action, false);
        return ret;
    }

    private String backendDesc() {
        return Settings.getString("pref_backend");
    }

    private String audiodesc() {
        return Settings.getString("pref_audio");
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        super.onGuidedActionClicked(action);
    }

    @Override
    public long onGuidedActionEditedAndProceed(GuidedAction action) {
        switch((int) action.getId()) {
            case ID_BACKEND_IP:
                String newVal = action.getDescription().toString();
                mEditor.putString("pref_backend",newVal);
                mBackendAction.setDescription(newVal);
                notifyActionChanged(findActionPositionById(ID_BACKEND));
                break;
            case ID_HTTP_PORT:
                mEditor.putString("pref_http_port",
                    validateNumber(action, 1, 65535, 6544));
                break;
            case ID_BACKEND_MAC:
                mEditor.putString("pref_backend_mac",action.getDescription().toString());
                break;
            case ID_SKIP_FWD:
                mEditor.putString("pref_skip_fwd",action.getDescription().toString());
                break;
            case ID_SKIP_BACK:
                mEditor.putString("pref_skip_back",action.getDescription().toString());
                break;
            case ID_JUMP:
                mEditor.putString("pref_jump",action.getDescription().toString());
                break;
            case ID_LIVETV_DURATION:
                mEditor.putString("pref_livetv_duration",
                        validateNumber(action, 30, 240, 60));
                break;
            case ID_SUBTITLE_SIZE:
                mEditor.putString("pref_subtitle_size",
                        validateNumber(action, 25, 300, 100));
                break;
            case ID_RECENTS_DAYS:
                mEditor.putString("pref_recents_days",
                        validateNumber(action, 1, 60, 7));
                break;
            case ID_TWEAK_SEARCH_PKTS:
                mEditor.putString("pref_tweak_ts_search_pkts",
                        validateNumber(action, 600, 100000, 2600));
                break;
            default:
                return GuidedAction.ACTION_ID_CURRENT;
        }
        mEditor.apply();
        return GuidedAction.ACTION_ID_CURRENT;
    }

    private static String validateNumber(GuidedAction action, int min, int max, int defValue) {
        String s;
        int i;
        s = action.getDescription().toString();
        try {
            i = Integer.parseInt(s);
        } catch (Exception e) {
            i = defValue;
        }
        if (i < min)
            i = min;
        else if (i > max)
            i = max;
        s = String.valueOf(i);
        action.setDescription(s);
        return s;
    }

    @Override
    public void onGuidedActionEditCanceled(GuidedAction action) {
        switch((int) action.getId()) {
            case ID_BACKEND_IP:
                action.setDescription(Settings.getString("pref_backend"));
                break;
            case ID_BACKEND_MAC:
                action.setDescription(Settings.getString("pref_backend_mac"));
                break;
            case ID_HTTP_PORT:
                action.setDescription(Settings.getString("pref_http_port"));
                break;
            case ID_SKIP_FWD:
                action.setDescription(Settings.getString("pref_skip_fwd"));
                break;
            case ID_SKIP_BACK:
                action.setDescription(Settings.getString("pref_skip_back"));
                break;
            case ID_JUMP:
                action.setDescription(Settings.getString("pref_jump"));
                break;
            case ID_LIVETV_DURATION:
                action.setDescription(Settings.getString("pref_livetv_duration"));
                break;
            case ID_SUBTITLE_SIZE:
                action.setDescription(Settings.getString("pref_subtitle_size"));
                break;
            case ID_RECENTS_DAYS:
                action.setDescription(Settings.getString("pref_recents_days"));
                break;
            case ID_TWEAK_SEARCH_PKTS:
                action.setDescription(Settings.getString("pref_tweak_ts_search_pkts"));
                break;
        }
    }

    @Override
    public boolean onSubGuidedActionClicked(GuidedAction action) {

        int id = (int) action.getId();
        switch(id) {
            case ID_BOOKMARK_LOCAL:
                if (action.isChecked())
                    mEditor.putString("pref_bookmark", "local");
                else
                    mEditor.putString("pref_bookmark", "mythtv");
                break;
            case ID_FRAMERATE_MATCH:
                if (action.isChecked())
                    mEditor.putString("pref_framerate_match", "true");
                else
                    mEditor.putString("pref_framerate_match", "false");
                break;
            case ID_ARROW_JUMP:
                if (action.isChecked())
                    mEditor.putString("pref_arrow_jump", "true");
                else
                    mEditor.putString("pref_arrow_jump", "false");
                break;
            case ID_SORT_ORIG_AIRDATE:
                if (action.isChecked())
                    mEditor.putString("pref_seq", "airdate");
                else
                    mEditor.putString("pref_seq", "rectime");
                break;
            case ID_DESCENDING:
                if (action.isChecked())
                    mEditor.putString("pref_seq_ascdesc", "desc");
                else
                    mEditor.putString("pref_seq_ascdesc", "asc");
                break;
            case ID_SHOW_RECENTS:
                if (action.isChecked())
                    mEditor.putString("pref_show_recents", "true");
                else
                    mEditor.putString("pref_show_recents", "false");
                restart(ID_PROG_LIST_OPTIONS);
                break;
            case ID_RECENTS_DELETED:
                if (action.isChecked())
                    mEditor.putString("pref_recents_deleted", "true");
                else
                    mEditor.putString("pref_recents_deleted", "false");
                if (! "true".equals(Settings.getString("pref_recents_watched")))
                    restart(ID_PROG_LIST_OPTIONS);
                break;
            case ID_RECENTS_WATCHED:
                if (action.isChecked())
                    mEditor.putString("pref_recents_watched", "true");
                else
                    mEditor.putString("pref_recents_watched", "false");
                if (! "true".equals(Settings.getString("pref_recents_deleted")))
                    restart(ID_PROG_LIST_OPTIONS);
                break;
            case ID_RECENTS_TRIM:
                if (action.isChecked())
                    mEditor.putString("pref_recents_trim", "true");
                else
                    mEditor.putString("pref_recents_trim", "false");
                break;
            case ID_RELATED_DELETED:
                if (action.isChecked())
                    mEditor.putString("pref_related_deleted", "true");
                else
                    mEditor.putString("pref_related_deleted", "false");
                break;
            case ID_RELATED_WATCHED:
                if (action.isChecked())
                    mEditor.putString("pref_related_watched", "true");
                else
                    mEditor.putString("pref_related_watched", "false");
                break;
            case ID_AUDIO_AUTO:
                if (action.isChecked())
                    mEditor.putString("pref_audio", "auto");
                break;
            case ID_AUDIO_MEDIACODEC:
                if (action.isChecked())
                    mEditor.putString("pref_audio", "mediacodec");
                break;
            case ID_AUDIO_FFMPEG:
                if (action.isChecked())
                    mEditor.putString("pref_audio", "ffmpeg");
                break;
            case ID_ERROR_TOAST:
                if (action.isChecked())
                    mEditor.putString("pref_error_toast", "true");
                else
                    mEditor.putString("pref_error_toast", "false");
                break;
            case ID_LETTERBOX_BLACK:
                if (action.isChecked())
                    mEditor.putString("pref_letterbox_color", String.valueOf(Color.BLACK));
                else
                    mEditor.putString("pref_letterbox_color", String.valueOf(Color.DKGRAY));
                break;
            default:
                return false;
        }
        mEditor.apply();
        notifyActionChanged(findActionPositionById(ID_PLAYBACK));
        mAudioAction.setDescription(audiodesc());
        notifyActionChanged(findActionPositionById(ID_AUDIO));
        return false;
    }

    private void restart(int key) {
        // restart the activity so the recent options below get appropriately
        // enabled or disabled
        Intent intent = new Intent(getContext(), SettingsActivity.class);
        intent.putExtra(KEY_EXPAND, key);
        getContext().startActivity(intent);
        finishGuidedStepSupportFragments();
    }

    @Override
    public void onResume() {
        mPriorBackend = Settings.getString("pref_backend");
        mPriorHttpPort =  Settings.getString("pref_http_port");
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!Objects.equals(mPriorBackend, Settings.getString("pref_backend"))
          || !Objects.equals(mPriorHttpPort, Settings.getString("pref_http_port")))
            MainActivity.getContext().getMainFragment().startFetch(-1, null, null);
        mPriorBackend = null;
        mPriorHttpPort = null;
    }
}
