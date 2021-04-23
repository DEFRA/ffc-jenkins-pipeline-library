package uk.gov.defra.ffc

import uk.gov.defra.ffc.Utils
import json
import requests
import datetime
import hashlib
import hmac
import base64

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.InvalidKeyException
import java.io.File

class ConsoleLogs implements Serializable {
  static def save(ctx, jenkinsUrl, repoName, branch, buildNumber, logFilePath) {
    def logFileDateTime = new Date().format("yyyy-MM-dd_HH:mm:ss", TimeZone.getTimeZone('UTC'))

    def folder = Utils.getFolder(ctx)
    def url = "$jenkinsUrl/job/$folder/job/$repoName-build/job/$branch/$buildNumber/consoleText"

    saveLogFile(ctx, url, logFilePath, logFileDateTime)

    processLogFile(ctx, logFilePath, logFileDateTime)
  }

  static def save(ctx, jenkinsUrl, repoName, buildNumber, logFilePath) {
    def logFileDateTime = new Date().format("yyyy-MM-dd_HH:mm:ss", TimeZone.getTimeZone('UTC'))

    def folder = Utils.getFolder(ctx)
    def url = "$jenkinsUrl/job/$folder/job/$repoName-deploy/$buildNumber/consoleText"

    saveLogFile(ctx, url, logFilePath, logFileDateTime)

    processLogFile(ctx, logFilePath, logFileDateTime)
  }

  static def saveLogFile(ctx, url, logFilePath, logFileDateTime) {    
    
    ctx.sh("[ -d $logFilePath ]  && docker run --rm -u root --privileged --mount type=bind,source=$logFilePath,target=/home/node defradigital/node-development chown $ctx.JENKINS_USER_ID:$ctx.JENKINS_GROUP_ID -R -v .")
   
    def script = "curl $url > $logFilePath/log_${logFileDateTime}.txt"
    ctx.echo("script: $script")
    ctx.sh(script: script, returnStdout: true)

  }

  static processLogFile(ctx, logFilePath, logFileDateTime)

    ctx.withCredentials([
      ctx.string(credentialsId: 'log-analytics-customer-id', variable: 'customerId'),
      ctx.string(credentialsId: 'log-analytics-shared-key', variable: 'sharedKey'),
      ctx.string(credentialsId: 'log-analytics-url', variable: 'url'),
    ]) {
      
      String logType = 'JenkinsExample'

      String method = 'POST'
      String contentType = 'application/json'
      String resource = '/api/logs'

      def now = new Date().format("EEE, dd MMM yyyy HH:mm:ss zzz", TimeZone.getTimeZone('GMT'))

      String json = readJsonFromLogFile("$logFilePath/log_${logFileDateTime}.txt")
          
      postData(customerId, sharedKey, json, method, contentType, resource, logType, url, now.toString())

    }
  }

  static def readJsonFromLogFile(String fileName) {

    String json = ''  
    def lines = new File(fileName).eachLine { line ->
    
      json = json + ',{"date":"' + new Date().format("EEE, dd MMM yyyy HH:mm:ss zzz", TimeZone.getTimeZone('GMT')) + '", "text":"' + line.replace("’", "").replace("‘", "").replace('"', '').replace('//', '').replace("'", "").replace(/"/, /\"/).replace(/`/, /\`/).replace("'", /'"'"'/).replace('\\', '\\\\\\\\').replace('', '').replace('●', '').replace('⎈', '').replace('✓', '').replace('❤', '').replace('✔', '').replace('£','').replace('©','') + '"}'      
    }

    return '['+ json.substring(1) + ']'
  }

  static def hmac_sha256(String secretKey, String data) {
  
    Mac mac = Mac.getInstance("HmacSHA256")
    SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.decodeBase64(), "HmacSHA256")
    mac.init(secretKeySpec)
    return mac.doFinal(data.getBytes("UTF-8"))
  }

  // Build the API signature
  static def build_signature(customerId, sharedKey, date, contentLength, method, contentType, resource) {
      
    def xHeaders = 'x-ms-date:' + date
    def stringToHash = method + '\n' + contentLength.toString() + '\n' + contentType + '\n' + xHeaders + "\n" + resource
    def bytesToHash = stringToHash.getBytes("UTF-8")
    
    def decodedKey = sharedKey.decodeBase64()  

    def encodedHash = hmac_sha256(sharedKey, stringToHash).encodeBase64().toString()
    def authorization = 'SharedKey ' + customerId + ':' + encodedHash  
    return authorization
  }

  // Build and send a request to the POST API
  static def postData(customerId, sharedKey, json, method, contentType, resource, logType, url, now) {
  
    def uri = new URL(url).openConnection() as HttpURLConnection
        
    def signature = build_signature(customerId, sharedKey, now, json.length(), method, contentType, resource)
    
    uri.setRequestMethod('POST')
    uri.setDoOutput(true)
    uri.setRequestProperty("Content-Type", 'application/json')
    uri.setRequestProperty("Authorization", signature)
    uri.setRequestProperty("Log-Type", logType)
    uri.setRequestProperty("x-ms-date", now.toString())

    uri.outputStream.write(json.getBytes("UTF-8"))
    uri.connect()
    def postRC = uri.getResponseCode();
    println("response code: " + postRC)
    if(postRC.equals(200)) {
        println(uri.getInputStream().getText())
    }
  }

