
/**
 * Make generic HTTP call and return parsed JSON
 *
 * @param url       URL to make the request against
 * @param method    HTTP method to use (default GET)
 * @param data      JSON data to POST or PUT
 * @param headers   Map of additional request headers
 */
@NonCPS
def sendRequest(url, method = 'GET', data = null, headers = [:]) {

    def connection = new URL(url).openConnection()
    if (method != 'GET') {
        connection.setRequestMethod(method)
    }

    if (data) {
        headers['Content-Type'] = 'application/json'
    }

    headers['User-Agent'] = 'jenkins-groovy'
    headers['Accept'] = 'application/json'

    for (header in headers) {
        connection.setRequestProperty(header.key, header.value)
    }

    if (data) {
        connection.setDoOutput(true)
        if (data instanceof String) {
            dataStr = data
        } else {
            dataStr = new groovy.json.JsonBuilder(data).toString()
        }
        def output = new OutputStreamWriter(connection.outputStream)
        echo("[HTTP Request] URL: ${url}, method: ${method}, headers: ${headers}, content: ${dataStr}")
        output.write(dataStr)
        output.close()
    }

    if ( connection.responseCode == 200 ) {
        response = connection.inputStream.text
        try {
            response_content = new groovy.json.JsonSlurperClassic().parseText(response)
        } catch (groovy.json.JsonException e) {
            response_content = response
        }
        echo("[HTTP Response] Content: ${response_content}")
        return response_content
    } else {
        throw new Exception(connection.responseCode + ": " + connection.inputStream.text)
    }

}

/**
 * Make HTTP GET request
 *
 * @param url     URL which will requested
 * @param data    JSON data to PUT
 */
def sendGetRequest(url, data = null, headers = [:]) {
    return sendRequest(url, 'GET', data, headers)
}

/**
 * Make HTTP POST request
 *
 * @param url     URL which will requested
 * @param data    JSON data to PUT
 */
def sendPostRequest(url, data = null, headers = [:]) {
    return sendRequest(url, 'POST', data, headers)
}

return this;
