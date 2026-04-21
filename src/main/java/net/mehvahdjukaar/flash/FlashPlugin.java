package net.mehvahdjukaar.flash;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.APP)
public final class FlashPlugin implements Disposable, StartupActivity {

    public static final Logger LOGGER = Logger.getInstance(FlashPlugin.class);


    private static FlashlightHTTPServer currentServer = null;

    public FlashPlugin() {
        if (currentServer == null) {
            try {
                currentServer = new FlashlightHTTPServer();
            } catch (Exception e) {
                LOGGER.error("Error starting Flashlight HTTP server", e);
            }
        }
    }

    @Override
    public void dispose() {
        if (currentServer != null) {
            try {
                currentServer.stop();
                currentServer = null;
            } catch (Exception e) {
                LOGGER.error("Error stopping Flashlight HTTP server", e);
            }
        }
    }

    @Override
    public void runActivity(@NotNull Project project) {
        project.getService(FlashPlugin.class);
    }
}
