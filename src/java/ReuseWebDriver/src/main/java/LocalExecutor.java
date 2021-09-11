import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.Response;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;

public class LocalExecutor extends HttpCommandExecutor {
    private final String sessionId;
    private final Capabilities capabilities;

    public LocalExecutor(String sessionId, URL addressOfRemoteServer, Capabilities capabilities) {
        super(addressOfRemoteServer);
        this.sessionId = sessionId;
        this.capabilities = capabilities;
    }

    @Override
    public Response execute(Command command) throws IOException {
        // invoke selenium command using the original driver executor
        if (!command.getName().equals("newSession"))
        {
            return super.execute(command);
        }

        // returns a success result, if the command is "newSession"
        // this prevents a shadow browser from opening
        Response response = new Response();
        response.setSessionId(sessionId);
        response.setState("success");
        response.setStatus(0);
        return response;
    }
}
