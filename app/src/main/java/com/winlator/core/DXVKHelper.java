package com.winlator.core;

import android.content.Context;

import com.winlator.core.envvars.EnvVars;
import com.winlator.xenvironment.ImageFs;

import java.io.File;

public class DXVKHelper {
    public static final String DEFAULT_CONFIG = "version="+DefaultVersion.DXVK+",framerate=0,maxDeviceMemory=0";

    public static KeyValueSet parseConfig(Object config) {
        String data = config != null && !config.toString().isEmpty() ? config.toString() : DEFAULT_CONFIG;
        return new KeyValueSet(data);
    }

    public static void setEnvVars(Context context, KeyValueSet config, EnvVars envVars) {
        ImageFs imageFs = ImageFs.find(context);
        envVars.put("DXVK_STATE_CACHE_PATH", "/data/data/app.gamenative/files/imagefs"+ImageFs.CACHE_PATH);
        envVars.put("DXVK_LOG_LEVEL", "none");
        envVars.put("DXVK_HUD", "none");

        // Task 3 — Audio: 60ms Pulse buffer so Wine audio thread doesn't block the game thread
        envVars.put("PULSE_LATENCY_MSEC", "60");
        String existingOverrides = envVars.get("WINEDLLOVERRIDES");
        String appendOverrides = "winepulse.drv=n,b;d3d8=n,b"; // Task 6 — D8VK for DX8 games
        envVars.put("WINEDLLOVERRIDES",
            (existingOverrides == null || existingOverrides.trim().isEmpty())
                ? appendOverrides
                : existingOverrides + ";" + appendOverrides);

        File rootDir = ImageFs.find(context).getRootDir();
        File dxvkConfigFile = new File(imageFs.config_path+"/dxvk.conf");

        String content = "\"";
        String maxDeviceMemory = config.get("maxDeviceMemory");
        if (!maxDeviceMemory.isEmpty() && !maxDeviceMemory.equals("0")) {
            content += "dxgi.maxDeviceMemory = "+maxDeviceMemory+"\n";
            content += "dxgi.maxSharedMemory = "+maxDeviceMemory+"\n";
        }

        String maxFeatureLevel = config.get("maxFeatureLevel");
        if (!maxFeatureLevel.isEmpty() && !maxFeatureLevel.equals("0")) {
            content += "d3d11.maxFeatureLevel = "+maxFeatureLevel+"\n";
            envVars.put("DXVK_FEATURE_LEVEL", maxFeatureLevel);
        }


        String framerate = config.get("framerate");
        if (!framerate.isEmpty() && !framerate.equals("0")) {
            envVars.put("DXVK_FRAME_RATE", framerate);
        }
        String customDevice = config.get("customDevice");
        if (customDevice.contains(":")) {
            String[] parts = customDevice.split(":");
            content = (((((content + "dxgi.customDeviceId = " + parts[0] + "\n") + "dxgi.customVendorId = " + parts[1] + "\n") + "d3d9.customDeviceId = " + parts[0] + "\n") + "d3d9.customVendorId = " + parts[1] + "\n") + "dxgi.customDeviceDesc = \"" + parts[2] + "\"\n") + "d3d9.customDeviceDesc = \"" + parts[2] + "\"\n";
        }
        if (config.getBoolean("constantBufferRangeCheck")) {
            content = content + "d3d11.constantBufferRangeCheck = \"True\"\n";
        }

        // DXVK_ASYNC: always enable regardless of the container's stored async= value.
        // Synchronous shader compilation blocks the render thread and causes the main-menu freeze.
        envVars.put("DXVK_ASYNC", "1");

        // DXVK_GPLASYNCCACHE: always enable the GPU-side pipeline async cache.
        // Without this, DXVK rebuilds the pipeline cache from scratch every session.
        // Previously this was conditional on asyncCache != "0", which meant it was disabled
        // for any container created with the default config (asyncCache=0).
        envVars.put("DXVK_GPLASYNCCACHE", "1");

        content = content + '\"';

        envVars.put("DXVK_CONFIG_FILE", rootDir + ImageFs.CONFIG_PATH+"/dxvk.conf");
        // Keep DXVK_CONFIG as a quoted-empty string — this is the safe baseline value that
        // Winlator's D8VK/DXVK builds expect. LaunchOrchestrator.applyGameConfig overrides
        // DXVK_CONFIG_FILE with the per-game dxvk.conf path (which contains enableAsync=True).
        // Do NOT inject a multi-line inline config here: D8VK (used in "Wrapper" graphics mode)
        // does not recognise all standard DXVK config keys and will abort on unknown entries.
        envVars.put("DXVK_CONFIG", content);
    }

    public static void setVKD3DEnvVars(Context context, KeyValueSet config, EnvVars envVars) {
        String featureLevel = config.get("vkd3dFeatureLevel", "12_1");
        envVars.put("VKD3D_FEATURE_LEVEL", featureLevel);
        // GameNative performance: pipeline cache, no RT (Adreno has none), worker threads, no debug
        // Task 10 — Append force_static_cbv to reduce descriptor heap pressure in large DX12 games
        String existingVkd3dConfig = envVars.get("VKD3D_CONFIG");
        if (existingVkd3dConfig == null || existingVkd3dConfig.isEmpty())
            existingVkd3dConfig = "pipeline_library_app_cache,no_upload_hvv";
        if (!existingVkd3dConfig.contains("force_static_cbv"))
            existingVkd3dConfig = existingVkd3dConfig + ",force_static_cbv";
        envVars.put("VKD3D_CONFIG", existingVkd3dConfig);
        envVars.put("VKD3D_DESCRIPTOR_QA_CHECKS", "0");
        envVars.put("VKD3D_SHADER_MODEL", "6_5");
        envVars.put("VKD3D_DISABLE_EXTENSIONS", "VK_KHR_ray_tracing_pipeline,VK_KHR_ray_query");
        envVars.put("VKD3D_WORKER_THREAD_COUNT", "4");
        envVars.put("VKD3D_DEBUG", "none");
        envVars.put("VKD3D_SHADER_DEBUG", "none");
        // Cache path: use same base as DXVK so per-game isolation can override via LaunchOrchestrator
        ImageFs imageFs = ImageFs.find(context);
        envVars.put("VKD3D_SHADER_CACHE_PATH", "/data/data/app.gamenative/files/imagefs" + ImageFs.CACHE_PATH + "/vkd3d_cache");
    }
}
