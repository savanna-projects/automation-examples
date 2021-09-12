using OpenQA.Selenium;
using OpenQA.Selenium.Chrome;
using OpenQA.Selenium.Remote;

using ReuseWebDrver;

using System;
using System.Reflection;

// start with browser one
var driverOne = new ChromeDriver();
driverOne.Manage().Window.Maximize();

// setup: get information to initialize driver two
var sessionId = $"{driverOne.SessionId}";
var addressOfRemoteServer = driverOne.GetEndpoint();
var timeout = TimeSpan.FromSeconds(60);
var commandExecutor = new LocalExecutor(sessionId, addressOfRemoteServer, timeout);
var desiredCapabilities = (driverOne as RemoteWebDriver)?.Capabilities;;

// mount browser one with browser two
_ = new RemoteWebDriver(commandExecutor, desiredCapabilities)
{
    Url = "https://www.google.com"
};

// close
driverOne.Dispose();

/*
 * # Local ICommandExecutor
 * Implement a local command executor of override the Execute command function of ICommandExecutor.
 * 
 * # Purpose
 * 1. Allows to avoid the shadow browser that opens each time you "mount" an existing browser.
 * 2. Allows to pass "Session ID" in the constructor.
 */
namespace ReuseWebDrver
{
    public class LocalExecutor : HttpCommandExecutor
    {
        public LocalExecutor(string sessionId, Uri addressOfRemoteServer, TimeSpan timeout)
            : this(sessionId, addressOfRemoteServer, timeout, false)
        { }

        public LocalExecutor(string sessionId, Uri addressOfRemoteServer, TimeSpan timeout, bool enableKeepAlive)
            : this(addressOfRemoteServer, timeout, enableKeepAlive)
        {
            SessionId = sessionId;
        }

        public LocalExecutor(Uri addressOfRemoteServer, TimeSpan timeout, bool enableKeepAlive)
            : base(addressOfRemoteServer, timeout, enableKeepAlive)
        { }

        // expose the "Session ID" used to build this executor
        public string SessionId { get; }

        public override Response Execute(Command commandToExecute)
        {
            // invoke selenium command using the original driver executor
            if (!commandToExecute.Name.Equals("newSession"))
            {
                return base.Execute(commandToExecute);
            }

            // returns a success result, if the command is "newSession"
            // this prevents a shadow browser from opening
            return new Response
            {
                Status = WebDriverResult.Success,
                SessionId = SessionId,
                Value = null
            };
        }
    }

    static class Extensions
    {
        public static string GetSession(this IWebDriver driver)
        {
            return driver is IHasSessionId id ? $"{id.SessionId}" : $"{Guid.NewGuid()}";
        }

        public static Uri GetEndpoint(this IWebDriver driver)
        {
            // setup
            const BindingFlags Flags = BindingFlags.Instance | BindingFlags.NonPublic;

            // get RemoteWebDriver type
            var remoteWebDriver = GetRemoteWebDriver(driver.GetType());

            // get this instance executor > get this instance internalExecutor
            var executor = remoteWebDriver.GetField("executor", Flags).GetValue(driver) as ICommandExecutor;

            // get URL
            var endpoint = executor.GetType().GetField("service", Flags).GetValue(executor) as DriverService;

            // result
            return endpoint.ServiceUrl;
        }

        private static Type GetRemoteWebDriver(Type type)
        {
            // if not a remote web driver, return the type used for the call
            if (!typeof(RemoteWebDriver).IsAssignableFrom(type))
            {
                return type;
            }

            // iterate until gets the RemoteWebDriver type
            while (type != typeof(RemoteWebDriver))
            {
                type = type.BaseType;
            }

            // gets RemoteWebDriver to use for extracting internal information
            return type;
        }
    }
}
