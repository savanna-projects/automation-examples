import org.openqa.selenium.*;
import org.openqa.selenium.logging.LocalLogs;
import org.openqa.selenium.logging.profiler.HttpProfilerLogEntry;
import org.openqa.selenium.remote.*;
import org.openqa.selenium.remote.http.HttpClient;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;

public class LocalExecutor extends HttpCommandExecutor {
    // members: constants
    private static final String NEW_SESSION = "newSession";
    private static final String PROFILER = "profiler";
    private final Map<String, CommandInfo> additionalCommands;
    private final HttpClient client;
    private final HttpClient.Factory httpClientFactory;
    private final LocalLogs logs;
    private final String sessionId;

    // members: state
    private CommandCodec<HttpRequest> commandCodec;
    private ResponseCodec<HttpResponse> responseCodec;

    public LocalExecutor(String sessionId, URL addressOfRemoteServer) {
        super(addressOfRemoteServer);
        this.additionalCommands = getReflectedField("additionalCommands", this);
        this.client = getReflectedField("client", this);
        this.httpClientFactory = getReflectedField("httpClientFactory", this);
        this.logs = getReflectedField("logs", this);
        this.sessionId = sessionId;
    }

    @Override
    public Response execute(Command command) {
        // setup
        Dialect dialect = Dialect.W3C;
        this.commandCodec = dialect.getCommandCodec();
        this.responseCodec = dialect.getResponseCodec();

        // invoke selenium command using the original driver code
        if (!command.getName().equals(NEW_SESSION)) {
            return invokeBaseCommand(command);
        }

        /*┌[ Simulate `newSession` Command ]───────────────────
          │
          │ This section was deigned to simulate a successful
          │ `newSession` command without opening a new browser.
          └────────────────────────────────────────────────────*/
        // log
        this.logs.addEntry(PROFILER, new HttpProfilerLogEntry(command.getName(), true));

        // build
        for (Map.Entry<String, CommandInfo> entry : this.additionalCommands.entrySet()) {
            this.defineCommand(entry.getKey(), entry.getValue());
        }

        // log
        this.logs.addEntry(PROFILER, new HttpProfilerLogEntry(command.getName(), false));

        // get
        return getSuccessResponse();
    }

    private Response invokeBaseCommand(Command command) {
        // assert session
        assertSession(command);

        // setup conditions
        boolean isSessionNull = command.getSessionId() == null;
        boolean isQuitCommand = "quit".equals(command.getName());

        // bad request
        if(isSessionNull && isQuitCommand){
            return new Response();
        }

        // mounted session was not created
        if (this.commandCodec == null || this.responseCodec == null) {
            throw new WebDriverException("No command or response codec has been defined. Unable to proceed");
        }

        // invoke base command
        HttpRequest httpRequest = this.commandCodec.encode(command);
        if (httpRequest.getHeader("Content-Type") == null) {
            httpRequest.addHeader("Content-Type", "application/json; charset=utf-8");
        }

        try {
            this.logs.addEntry(PROFILER,  new HttpProfilerLogEntry(command.getName(), true));
            HttpResponse httpResponse = this.client.execute(httpRequest);

            this.logs.addEntry(PROFILER,  new HttpProfilerLogEntry(command.getName(), false));
            Response response = this.responseCodec.decode(httpResponse);

            if (response.getSessionId() == null) {
                if (httpResponse.getTargetHost() != null) {
                    response.setSessionId(HttpSessionId.getSessionId(httpResponse.getTargetHost()).orElse(null));
                } else {
                    response.setSessionId(command.getSessionId().toString());
                }
            }

            if ("quit".equals(command.getName())) {
                this.client.close();
                this.httpClientFactory.cleanupIdleClients();
            }

            return response;
        } catch (UnsupportedCommandException e) {
            if (e.getMessage() != null && !"".equals(e.getMessage())) {
                throw e;
            } else {
                String message = "No information from server. Command name was: " + command.getName();
                throw new UnsupportedOperationException(message, e.getCause());
            }
        }
    }

    private static void assertSession(Command command){
        // no session commands
        boolean isSessionNull = command.getSessionId() == null;
        boolean isNewSessionCommand = NEW_SESSION.equals(command.getName());
        boolean isGetAllSessionsCommand = "getAllSessions".equals(command.getName());

        // assert
        if(isSessionNull && !isGetAllSessionsCommand && !isNewSessionCommand) {
            throw new NoSuchSessionException("Session ID is null. Using WebDriver after calling quit()?");
        }
    }

    public Response getSuccessResponse() {
        // setup
        Map<String, Object> capabilitiesMap = new HashMap<>();

        // returns a success result, if the command is "newSession"
        // this prevents a shadow browser from opening
        Response response = new Response();
        response.setValue(capabilitiesMap);
        response.setSessionId(sessionId);
        response.setState("success");
        response.setStatus(0);

        // get
        return response;
    }

    // Utilities
    // used only for safe casts under this class
    @SuppressWarnings("unchecked")
    private static <T> T getReflectedField(String name, Object instance) {
        try {
            Field field = HttpCommandExecutor.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(instance);
        } catch (Exception e) {
            // ignore exceptions
        }
        return null;
    }
}
