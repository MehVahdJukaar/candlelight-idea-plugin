package net.mehvahdjukaar.flash;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.startup.StartupActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FlashPlugin implements Disposable, ProjectActivity {

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
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        return null;
    }
}
