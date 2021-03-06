/**
 * Copyright (C) 2009-2013 Couchbase, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALING
 * IN THE SOFTWARE.
 */

package com.couchbase.client.vbucket.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import net.spy.memcached.HashAlgorithm;
import net.spy.memcached.HashAlgorithmRegistry;
import net.spy.memcached.compat.SpyObject;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * A DefaultConfigFactory.
 */
public class DefaultConfigFactory extends SpyObject implements ConfigFactory {

  @Override
  public Config create(File filename) {
    if (filename == null || "".equals(filename.getName())) {
      throw new IllegalArgumentException("Filename is empty.");
    }
    StringBuilder sb = new StringBuilder();
    try {
      FileInputStream fis = new FileInputStream(filename);
      BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
      String str;
      while ((str = reader.readLine()) != null) {
        sb.append(str);
      }
    } catch (IOException e) {
      throw new ConfigParsingException("Exception reading input file: "
          + filename, e);
    }
    return create(sb.toString());
  }

  @Override
  public Config create(String data) {
    try {
      JSONObject jsonObject = new JSONObject(data);
      return parseJSON(jsonObject);
    } catch (JSONException e) {
      throw new ConfigParsingException("Exception parsing JSON data: " + data,
        e);
    }
  }

  @Override
  public Config create(JSONObject jsonObject) {
    try {
      return parseJSON(jsonObject);
    } catch (JSONException e) {
      throw new ConfigParsingException("Exception parsing JSON data: "
        + jsonObject, e);
    }
  }

  private Config parseJSON(JSONObject jsonObject) throws JSONException {
    // the incoming config could be cache or EP object types, JSON envelope
    // picked apart
    if (!jsonObject.has("vBucketServerMap")) {
      return parseCacheJSON(jsonObject);
    }
    return parseEpJSON(jsonObject);
  }

  private Config parseCacheJSON(JSONObject jsonObject) throws JSONException {

    JSONArray nodes = jsonObject.getJSONArray("nodes");
    if (nodes.length() <= 0) {
      throw new ConfigParsingException("Empty nodes list.");
    }
    int serversCount = nodes.length();

    CacheConfig config = new CacheConfig(serversCount);
    populateServers(config, nodes);

    return config;
  }

  /* ep is for ep-engine, a.k.a. couchbase */
  private Config parseEpJSON(JSONObject jsonObject) throws JSONException {
    JSONObject vbMap = jsonObject.getJSONObject("vBucketServerMap");
    String algorithm = vbMap.getString("hashAlgorithm");
    HashAlgorithm hashAlgorithm =
        HashAlgorithmRegistry.lookupHashAlgorithm(algorithm);
    if (hashAlgorithm == null) {
      throw new IllegalArgumentException("Unhandled hash algorithm type: "
          + algorithm);
    }
    int replicasCount = vbMap.getInt("numReplicas");
    if (replicasCount > VBucket.MAX_REPLICAS) {
      throw new ConfigParsingException("Expected number <= "
          + VBucket.MAX_REPLICAS + " for replicas.");
    }
    JSONArray servers = vbMap.getJSONArray("serverList");
    if (servers.length() <= 0) {
      throw new ConfigParsingException("Empty servers list.");
    }
    int serversCount = servers.length();
    JSONArray vbuckets = vbMap.getJSONArray("vBucketMap");
    int vbucketsCount = vbuckets.length();
    if (vbucketsCount == 0 || (vbucketsCount & (vbucketsCount - 1)) != 0) {
      throw new ConfigParsingException("Number of buckets must be a power of "
        + "two, > 0 and <= " + VBucket.MAX_BUCKETS);
    }
    List<String> populateServers = populateServers(servers);
    List<VBucket> populateVbuckets = populateVbuckets(vbuckets);

    List<URL> couchServers =
      populateCouchServers(jsonObject.getJSONArray("nodes"));

    DefaultConfig config = new DefaultConfig(hashAlgorithm, serversCount,
      replicasCount, vbucketsCount, populateServers, populateVbuckets,
      couchServers);

    return config;
  }

  private List<URL> populateCouchServers(JSONArray nodes) throws JSONException{
    List<URL> nodeNames = new ArrayList<URL>();
    for (int i = 0; i < nodes.length(); i++) {
      JSONObject node = nodes.getJSONObject(i);
      if (node.has("couchApiBase")) {
        try {
          nodeNames.add(new URL(node.getString("couchApiBase")));
        } catch (MalformedURLException e) {
          throw new JSONException("Got bad couchApiBase URL from config");
        }
      }
    }
    return nodeNames;
  }

  private List<String> populateServers(JSONArray servers) throws JSONException {
    List<String> serverNames = new ArrayList<String>();
    for (int i = 0; i < servers.length(); i++) {
      String server = servers.getString(i);
      serverNames.add(server);
    }
    return serverNames;
  }

  private void populateServers(CacheConfig config, JSONArray nodes)
    throws JSONException {
    List<String> serverNames = new ArrayList<String>();
    for (int i = 0; i < nodes.length(); i++) {
      JSONObject node = nodes.getJSONObject(i);
      String webHostPort = node.getString("hostname");
      String[] splitHostPort = webHostPort.split(":");
      JSONObject portsList = node.getJSONObject("ports");
      int port = portsList.getInt("direct");
      serverNames.add(splitHostPort[0] + ":" + port);
    }
    config.setServers(serverNames);
  }

  private List<VBucket> populateVbuckets(JSONArray jsonVbuckets)
    throws JSONException {
    List<VBucket> vBuckets = new ArrayList<VBucket>();
    for (int i = 0; i < jsonVbuckets.length(); i++) {
      JSONArray rows = jsonVbuckets.getJSONArray(i);
      int master = rows.getInt(0);
      int[] replicas = new int[VBucket.MAX_REPLICAS];
      for (int j = 1; j < rows.length(); j++) {
        replicas[j - 1] = rows.getInt(j);
      }
      vBuckets.add(new VBucket(master, replicas));
    }
    return vBuckets;
  }
}
