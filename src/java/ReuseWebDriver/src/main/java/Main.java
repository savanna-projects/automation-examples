import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.remote.CommandExecutor;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.lang.reflect.Field;
import java.net.URL;

public class Main {
    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
        // start with browser one
        System.setProperty("webdriver.chrome.driver", "D:\\AutomationEnvironment\\WebDrivers\\msedgedriver.exe");
        EdgeDriver driverOne = new EdgeDriver();
        driverOne.manage().window().maximize();

        // setup: get information to initialize driver two
        String sessionId = driverOne.getSessionId().toString();
        URL addressOfRemoteServer = getEndpoint(driverOne);
        Capabilities desiredCapabilities = driverOne.getCapabilities();
        CommandExecutor commandExecutor = new LocalExecutor(sessionId, addressOfRemoteServer);

        // mount browser one with browser two
        RemoteWebDriver driverTwo = new RemoteWebDriver(commandExecutor, desiredCapabilities);
        driverTwo.get("https://www.google.com");

        // close
        driverOne.quit();
    }

    private static URL getEndpoint(WebDriver driver) throws NoSuchFieldException, IllegalAccessException {
        // get RemoteWebDriver type
        Class<?> remoteWebDriver = getRemoteWebDriver(driver.getClass());

        // get this instance executor > get this instance internalExecutor
        Field executorField = remoteWebDriver.getDeclaredField("executor");
        executorField.setAccessible(true);
        CommandExecutor executor = (CommandExecutor) executorField.get(driver);

        // get URL
        Field endpointField = getCommandExecutor(executor.getClass()).getDeclaredField("remoteServer");
        endpointField.setAccessible(true);

        // result
        return (URL) endpointField.get(executor);
    }

    private static Class<?> getRemoteWebDriver(Class<?> type) {
        // if not a remote web driver, return the type used for the call
        if (!RemoteWebDriver.class.isAssignableFrom(type)) {
            return type;
        }

        // iterate until gets the RemoteWebDriver type
        while (type != RemoteWebDriver.class) {
            type = type.getSuperclass();
        }

        // gets RemoteWebDriver to use for extracting internal information
        return type;
    }

    private static Class<?> getCommandExecutor(Class<?> type) {
        // if not a remote web driver, return the type used for the call
        if (!HttpCommandExecutor.class.isAssignableFrom(type)) {
            return type;
        }

        // iterate until gets the RemoteWebDriver type
        while (type != HttpCommandExecutor.class) {
            type = type.getSuperclass();
        }

        // gets RemoteWebDriver to use for extracting internal information
        return type;
    }
}