from selenium import webdriver
from selenium.webdriver.remote.webdriver import WebDriver


def mount_session(driver: WebDriver) -> WebDriver:
    # setup
    url = driver.command_executor._url
    session_id = driver.session_id
    #
    # the original executor from the WebDriver state
    execute = WebDriver.execute

    # override newSession command
    def local_executor(self, command, params=None):
        if command != "newSession":
            return execute(self, command, params)
        return {'success': 0, 'value': None, 'sessionId': session_id}

    # mount
    WebDriver.execute = local_executor
    new_driver = webdriver.Remote(command_executor=url, desired_capabilities={})
    new_driver.session_id = session_id

    # restore original functionality
    WebDriver.execute = execute

    # get
    return new_driver


# start with browser one
browser_one: WebDriver = webdriver.Chrome("chromedriver.exe")
browser_one.maximize_window()

# mount browser one with browser two
browser_two = mount_session(driver=browser_one)
browser_two.get('https://www.google.com')
browser_one.quit()
