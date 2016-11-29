/*
 *  This file is part of Waltz.
 *
 *     Waltz is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Waltz is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Waltz.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.khartec.waltz.web;

import com.khartec.waltz.common.LoggingUtilities;
import com.khartec.waltz.common.exception.DuplicateKeyException;
import com.khartec.waltz.common.exception.InsufficientPrivelegeException;
import com.khartec.waltz.service.DIConfiguration;
import com.khartec.waltz.service.settings.SettingsService;
import com.khartec.waltz.web.endpoints.Endpoint;
import com.khartec.waltz.web.endpoints.api.StaticResourcesEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.Map;

import static com.khartec.waltz.web.WebUtilities.reportException;
import static spark.Spark.*;

public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private final static String GZIP_ENABLED_NAME = "server.gzip.enabled";
    private final static String GZIP_MIN_SIZE_NAME = "server.gzip.minimum-size";

    private static AnnotationConfigApplicationContext ctx;

    public static void main(String[] args) {
        new Main().go();
    }


    public static AnnotationConfigApplicationContext getSpringContext() {
        return ctx;
    }


    public void go() {
        startHttpServer();
    }


    private void startHttpServer() {
        String listenPortStr = System.getProperty("waltz.port", "8443");
        boolean sslEnabled = Boolean.valueOf(System.getProperty("waltz.ssl.enabled", "false"));

        String home = System.getProperty("user.home");
        boolean devMode = Boolean.valueOf(System.getProperty("waltz.dev.mode", "false"));

        final ServerMode mode = devMode ? ServerMode.DEV : ServerMode.DEPLOY;


        System.out.println("--WALTZ---------------------------------------------");
        System.out.println("Home is: " + home);
        System.out.println("Mode is: " + mode);
        System.out.println("Listening on port: " + listenPortStr);
        System.out.println("SSL Enabled: " + sslEnabled);
        System.out.println("----------------------------------------------------");

        if (sslEnabled) {
            Spark.secure(home + "/.waltz/keystore.jks", "password", null, null);
        }

        int listenPort = Integer.parseInt(listenPortStr);
        port(listenPort);


        start(mode);
    }

    public void start(ServerMode mode) {
        // configure logging
        LoggingUtilities.configureLogging();

        ctx = new AnnotationConfigApplicationContext(DIConfiguration.class);

        Map<String, Endpoint> endpoints = ctx.getBeansOfType(Endpoint.class);
        endpoints.forEach((name, endpoint) -> {
            LOG.info("Registering Endpoint: {}", name);
            endpoint.register();
        });


        new StaticResourcesEndpoint().register();

        LOG.info("Completed endpoint registration");

        registerExceptionHandlers();
        enableGZIP();
        enableCORS();

    }


    private void registerExceptionHandlers() {
        exception(WebException.class, (e, req, res) -> {
            LOG.error(e.getMessage());
            WebException we = (WebException) e;
            reportException(
                    500,
                    we.getCode(),
                    we.getMessage(),
                    res,
                    LOG);
        });

        exception(DuplicateKeyException.class, (e, req, res) -> {
            String message = "Duplicate detected";
            LOG.error(message, e);
            reportException(
                    500,
                    "DUPLICATE",
                    message,
                    res,
                    LOG);
        });

        exception(InsufficientPrivelegeException.class, (e, req, resp) ->
           reportException(403, "NOT_AUTHORIZED", e.getMessage(), resp, LOG));


        exception(Exception.class, (e, req, res) -> {
            String message = "Exception: " + e.getMessage() + " / " + e.getClass().getCanonicalName();
            LOG.error(message, e);
            reportException(
                    500,
                    "unknown",
                    message,
                    res,
                    LOG);
        });
    }


    private void enableGZIP() {
        SettingsService settingsService = ctx.getBean(SettingsService.class);

        Boolean gzipEnabled = settingsService
                .getValue(GZIP_ENABLED_NAME)
                .map(x -> x.equalsIgnoreCase("true"))
                .orElse(false);

        if(gzipEnabled) {

            //now fetch the minimum size
            int minimumLength = settingsService
                    .getValue(GZIP_MIN_SIZE_NAME)
                    .map(Integer::parseInt)
                    .orElse(8192);

            after(((request, response) -> {
                if(response.body() != null && response.body().length() >= minimumLength) {
                    response.header("Content-Encoding", "gzip");
                }
            }));

            LOG.info("Enabled GZIP (size: " + minimumLength + ")");

        } else {
            LOG.info("GZIP not enabled");
        }


    }

    private void enableCORS() {

        options("/*", (req, res) -> {

            handleCORSHeader(req, res, "Access-Control-Request-Headers", "Access-Control-Allow-Headers");
            handleCORSHeader(req, res, "Access-Control-Request-Method", "Access-Control-Allow-Methods");

            return "OK";
        });

        before((req, res) -> res.header("Access-Control-Allow-Origin", "*"));
        LOG.info("Enabled CORS");
    }


    private void handleCORSHeader(Request req, Response res, String requestHeader, String responseHeader) {
        String accessControlRequestHeaders = req.headers(requestHeader);
        if (accessControlRequestHeaders != null) {
            res.header(responseHeader, accessControlRequestHeaders);
        }
    }

}
