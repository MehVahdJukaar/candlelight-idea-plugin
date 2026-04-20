package net.mehvahdjukaar.flash;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

public class MyHttpServer {
    private final Server server;

    public static void main(String[] args) throws Exception {
        new MyHttpServer();
    }

    public MyHttpServer() throws Exception {

        int port = 4303;
        System.out.println("Starting HTTP on port "+port);

        server = new Server(port);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);

        ResourceConfig config = new ResourceConfig().packages("net.mehvahdjukaar.flash.api");
        ServletHolder jerseyServlet = new ServletHolder(new ServletContainer(config));
        context.addServlet(jerseyServlet, "/*");

        server.start();
        System.out.println("HTTP Server started on port "+port);

    }

    public void stop() throws Exception {
        if (server != null) {
            server.stop();
            System.out.println("HTTP Server stopped");
        }
    }
}
