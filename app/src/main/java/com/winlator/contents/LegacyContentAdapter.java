package com.winlator.contents;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Infers a synthetic ContentProfile from upstream Wine/Proton tarballs that have no profile.json
 * (e.g. GloriousEggroll Proton-GE, Kron4ek Wine-Builds). Allows the version picker to install
 * these without repackaging.
 */
public final class LegacyContentAdapter {
    private static final String TAG = "LegacyContentAdapter";

    private LegacyContentAdapter() {}

    /**
     * Infer a ContentProfile from the extracted tarball root (tmp dir) when profile.json is missing.
     * Detects Proton-GE layout (files/bin, files/lib) and Kron4ek layout (bin, lib at content root).
     *
     * @param context   Application context (for path consistency; may be unused).
     * @param extractedRoot The tmp dir after TarCompressorUtils.extract (ContentsManager.getTmpDir).
     * @return A synthetic profile, or null if the layout is not recognized.
     */
    public static ContentProfile inferProfile(Context context, File extractedRoot) {
        if (extractedRoot == null || !extractedRoot.isDirectory()) return null;

        File contentRoot = findContentRoot(extractedRoot);
        String prefixRelativeToTmp = (contentRoot.equals(extractedRoot))
            ? "" : contentRoot.getName() + "/";

        // Proton-GE: contentRoot/files/bin, contentRoot/files/lib (or lib64)
        File geBin = new File(contentRoot, "files/bin");
        File geLib = new File(contentRoot, "files/lib");
        File geLib64 = new File(contentRoot, "files/lib64");
        if (geBin.isDirectory() && hasWineExecutable(geBin)) {
            String libPath = geLib.isDirectory() ? "lib" : (geLib64.isDirectory() ? "lib64" : null);
            if (libPath != null) {
                ContentProfile profile = new ContentProfile();
                profile.type = ContentProfile.ContentType.CONTENT_TYPE_PROTON;
                profile.verName = contentRoot.getName();
                profile.verCode = 1;
                profile.desc = "Legacy (upstream Proton-GE)";
                profile.fileList = new ArrayList<>();
                profile.wineBinPath = prefixRelativeToTmp + "files/bin";
                profile.wineLibPath = prefixRelativeToTmp + "files/" + libPath;
                profile.winePrefixPack = ensurePrefixPack(extractedRoot, contentRoot, prefixRelativeToTmp);
                if (profile.winePrefixPack == null) return null;
                Log.d(TAG, "Inferred Proton-GE profile: " + profile.verName + " bin=" + profile.wineBinPath + " lib=" + profile.wineLibPath);
                return profile;
            }
        }

        // Kron4ek Wine: contentRoot/bin, contentRoot/lib (or lib64)
        File wineBin = new File(contentRoot, "bin");
        File wineLib = new File(contentRoot, "lib");
        File wineLib64 = new File(contentRoot, "lib64");
        if (wineBin.isDirectory() && hasWineExecutable(wineBin)) {
            String libPath = wineLib.isDirectory() ? "lib" : (wineLib64.isDirectory() ? "lib64" : null);
            if (libPath != null) {
                ContentProfile profile = new ContentProfile();
                profile.type = ContentProfile.ContentType.CONTENT_TYPE_WINE;
                profile.verName = contentRoot.getName();
                profile.verCode = 1;
                profile.desc = "Legacy (upstream Wine)";
                profile.fileList = new ArrayList<>();
                profile.wineBinPath = prefixRelativeToTmp + "bin";
                profile.wineLibPath = prefixRelativeToTmp + libPath;
                profile.winePrefixPack = ensurePrefixPack(extractedRoot, contentRoot, prefixRelativeToTmp);
                if (profile.winePrefixPack == null) return null;
                Log.d(TAG, "Inferred Wine profile: " + profile.verName + " bin=" + profile.wineBinPath + " lib=" + profile.wineLibPath);
                return profile;
            }
        }

        return null;
    }

    private static File findContentRoot(File extractedRoot) {
        File[] children = extractedRoot.listFiles(File::isDirectory);
        if (children != null && children.length == 1) {
            return children[0];
        }
        return extractedRoot;
    }

    private static boolean hasWineExecutable(File binDir) {
        if (binDir == null || !binDir.isDirectory()) return false;
        File wine = new File(binDir, "wine");
        File wine64 = new File(binDir, "wine64");
        return (wine.exists() && wine.isFile()) || (wine64.exists() && wine64.isFile());
    }

    /**
     * Ensure a prefix pack path exists: find an existing one under content root, or create a placeholder.
     * Returns path relative to extractedRoot (tmp), or null on failure.
     */
    private static String ensurePrefixPack(File extractedRoot, File contentRoot, String prefixRelativeToTmp) {
        String[] prefixNames = {"prefix.tar.xz", "prefixPack.txz", "prefix.tar.gz", "prefixPack.tar.xz"};
        for (String name : prefixNames) {
            File f = new File(contentRoot, name);
            if (f.exists() && f.isFile()) {
                return prefixRelativeToTmp + name;
            }
        }
        // Create placeholder so ContentsManager validation passes
        String placeholderRelative = prefixRelativeToTmp + "prefixPack.placeholder";
        File placeholder = new File(extractedRoot, placeholderRelative);
        try {
            if (!placeholder.exists()) {
                placeholder.getParentFile().mkdirs();
                placeholder.createNewFile();
            }
            return placeholderRelative;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create prefix pack placeholder", e);
            return null;
        }
    }
}
