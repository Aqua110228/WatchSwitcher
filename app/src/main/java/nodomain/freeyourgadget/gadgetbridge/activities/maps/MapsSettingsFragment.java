/*  Copyright (C) 2025 José Rebelo

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities.maps;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.reader.header.MapFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractPreferenceFragment;
import nodomain.freeyourgadget.gadgetbridge.util.FormatUtils;
import nodomain.freeyourgadget.gadgetbridge.util.UriUtils;
import nodomain.freeyourgadget.gadgetbridge.util.maps.MapsManager;

public class MapsSettingsFragment extends AbstractPreferenceFragment {
    private static final Logger LOG = LoggerFactory.getLogger(MapsSettingsFragment.class);

    public static final String ACTION_SETTING_CHANGE = "nodomain.freeyourgadget.gadgetbridge.maps.setting_change";
    public static final String EXTRA_SETTING_KEY = "nodomain.freeyourgadget.gadgetbridge.maps_setting_key";
    private static final String PREF_CATEGORY_MAP_FILES = "pref_category_map_files";
    private static final String PREF_MAP_FILE_PREFIX = "pref_map_file_";

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState, @Nullable final String rootKey) {
        setPreferencesFromResource(R.xml.map_settings, rootKey);

        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (prefs == null) {
            requireActivity().finish();
            return;
        }

        final Preference prefDownload = Objects.requireNonNull(findPreference("maps_download"));
        prefDownload.setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://ftp-stud.hs-esslingen.de/pub/Mirrors/download.mapsforge.org/maps/v5/")
            ));
            return true;
        });

        final Preference prefFolder = Objects.requireNonNull(findPreference(MapsManager.PREF_MAPS_FOLDER));
        final ActivityResultLauncher<Uri> mapsFolderChooser = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                localUri -> {
                    LOG.info("Maps folder: {}", localUri);
                    if (localUri != null) {
                        requireContext().getContentResolver().takePersistableUriPermission(localUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        prefs.edit()
                                .putString(MapsManager.PREF_MAPS_FOLDER, localUri.toString())
                                .apply();
                        prefFolder.setSummary(UriUtils.INSTANCE.resolveLocationSummary(requireContext(), localUri.toString()));
                        broadcastPreferenceChange(MapsManager.PREF_MAPS_FOLDER);
                        refreshMapFiles();
                    }
                }
        );
        final String currentFolder = prefs.getString(MapsManager.PREF_MAPS_FOLDER, "");
        prefFolder.setSummary(UriUtils.INSTANCE.resolveLocationSummary(requireContext(), currentFolder));
        prefFolder.setOnPreferenceClickListener(preference -> {
            mapsFolderChooser.launch(null);
            return true;
        });

        refreshMapFiles();

        final Preference prefMapTheme = Objects.requireNonNull(findPreference(MapsManager.PREF_MAP_THEME));
        prefMapTheme.setOnPreferenceChangeListener((preference, newValue) -> {
            broadcastPreferenceChange(MapsManager.PREF_MAP_THEME);
            return true;
        });

        final Preference prefTrackColor = Objects.requireNonNull(findPreference(MapsManager.PREF_TRACK_COLOR));
        prefTrackColor.setOnPreferenceChangeListener((preference, newValue) -> {
            broadcastPreferenceChange(MapsManager.PREF_TRACK_COLOR);
            return true;
        });
    }

    private void refreshMapFiles() {
        final PreferenceCategory category = findPreference(PREF_CATEGORY_MAP_FILES);
        if (category == null) {
            return;
        }

        // Remove the dynamically added preferences from a previous refresh
        for (int i = 0; i < category.getPreferenceCount(); i++) {
            final Preference pref = category.getPreference(i);
            if (pref.getKey() != null && pref.getKey().startsWith(PREF_MAP_FILE_PREFIX)) {
                category.removePreference(pref);
            }
        }

        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (prefs == null) {
            return;
        }

        final List<DocumentFile> mapFiles = new ArrayList<>();
        final String folderUri = prefs.getString(MapsManager.PREF_MAPS_FOLDER, "");
        if (folderUri.isEmpty()) {
            // no folder selected
            category.setVisible(false);
            return;
        }

        final DocumentFile folder = DocumentFile.fromTreeUri(requireContext(), Uri.parse(folderUri));
        if (folder != null) {
            for (final DocumentFile documentFile : folder.listFiles()) {
                final String name = documentFile.getName();
                if (name != null && name.endsWith(".map")) {
                    mapFiles.add(documentFile);
                }
            }
        }

        // No map files
        if (mapFiles.isEmpty()) {
            final Preference emptyPref = new Preference(requireContext());
            emptyPref.setKey(PREF_MAP_FILE_PREFIX + "empty");
            emptyPref.setSelectable(false);
            emptyPref.setSummary(R.string.maps_folder_empty);
            emptyPref.setIconSpaceReserved(false);
            category.addPreference(emptyPref);
            return;
        }

        for (final DocumentFile documentFile : mapFiles) {
            final Preference pref = new Preference(requireContext());
            pref.setKey(PREF_MAP_FILE_PREFIX + documentFile.getUri());
            pref.setSelectable(false);
            pref.setTitle(documentFile.getName());
            pref.setIcon(R.drawable.ic_map);

            updateMapInfo(pref, documentFile);

            category.addPreference(pref);
        }
    }

    private void updateMapInfo(final Preference pref, final DocumentFile documentFile) {
        try {
            final FileInputStream inputStream = (FileInputStream) requireContext().getContentResolver().openInputStream(documentFile.getUri());
            if (inputStream == null) {
                throw new IOException("FileInputStream is null");
            }
            // Reading the header fails with a MapFileException
            final MapFile mapFile = new MapFile(inputStream, 0, null);
            final MapFileInfo mapFileInfo = mapFile.getMapFileInfo();

            final String formattedDate = DateUtils.formatDateTime(
                    requireContext(),
                    mapFileInfo.mapDate,
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME
            );
            pref.setSummary(formattedDate + "\n" + FormatUtils.formatBytes(mapFileInfo.fileSize));

            mapFile.close();
        } catch (final Exception e) {
            LOG.error("Failed to get MapFileInfo for {}", documentFile.getName(), e);
            pref.setSummary(requireContext().getString(R.string.maps_file_invalid, e.getMessage()));
        }
    }

    private void broadcastPreferenceChange(final String key) {
        final Intent intent = new Intent();
        intent.setAction(ACTION_SETTING_CHANGE);
        intent.putExtra(EXTRA_SETTING_KEY, key);
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent);
    }
}
