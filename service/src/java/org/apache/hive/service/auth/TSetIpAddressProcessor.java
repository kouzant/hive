/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hive.service.auth;

import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hive.service.rpc.thrift.TCLIService;
import org.apache.hive.service.rpc.thrift.TCLIService.Iface;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for setting the ipAddress for operations executed via HiveServer2.
 * <p>
 * <ul>
 * <li>IP address is only set for operations that calls listeners with hookContext</li>
 * <li>IP address is only set if the underlying transport mechanism is socket</li>
 * </ul>
 * </p>
 *
 * @see org.apache.hadoop.hive.ql.hooks.ExecuteWithHookContext
 */
public class TSetIpAddressProcessor<I extends Iface> extends TCLIService.Processor<Iface> {

  private static final Logger LOGGER = LoggerFactory.getLogger(TSetIpAddressProcessor.class.getName());
  private HiveConf hiveConf = null;

  public TSetIpAddressProcessor(Iface iface) {
    super(iface);
    hiveConf = new HiveConf(this.getClass());
  }

  @Override
  public boolean process(final TProtocol in, final TProtocol out) throws TException {
    setIpAddress(in);
    setUserName(in);
    try {
      return super.process(in, out);
    } finally {
      THREAD_LOCAL_USER_NAME.remove();
      THREAD_LOCAL_IP_ADDRESS.remove();
    }
  }

  /**
   * In Hops authentication mode (In non-SSL authentication mode) the username is not set
   * until the opening of the session. When the session is open a request is made to HopsWorks to
   * get the username of the user and the project specific username is built.
   * @param in
   */
  protected void setUserName(final TProtocol in) throws TException {
    TTransport transport = in.getTransport();
    if (transport instanceof TSaslServerTransport) {
      String userName = ((TSaslServerTransport) transport).getSaslServer().getAuthorizationID();
      THREAD_LOCAL_USER_NAME.set(userName);
    }
  }

  protected void setIpAddress(final TProtocol in) {
    TTransport transport = in.getTransport();
    TSocket tSocket = getUnderlyingSocketFromTransport(transport);
    if (tSocket == null) {
      LOGGER.warn("Unknown Transport, cannot determine ipAddress");
    } else {
      THREAD_LOCAL_IP_ADDRESS.set(tSocket.getSocket().getInetAddress().getHostAddress());
    }
  }

  protected TSocket getUnderlyingSocketFromTransport(TTransport transport) {
    while (transport != null) {
      if (transport instanceof TSaslServerTransport) {
        transport = ((TSaslServerTransport) transport).getUnderlyingTransport();
      }
      if (transport instanceof TSaslClientTransport) {
        transport = ((TSaslClientTransport) transport).getUnderlyingTransport();
      }
      if (transport instanceof TSocket) {
        return (TSocket) transport;
      }
    }
    return null;
  }

  protected static final ThreadLocal<String> THREAD_LOCAL_IP_ADDRESS = new ThreadLocal<String>() {
    @Override
    protected String initialValue() {
      return null;
    }
  };

  protected static final ThreadLocal<String> THREAD_LOCAL_USER_NAME = new ThreadLocal<String>() {
    @Override
    protected String initialValue() {
      return null;
    }
  };

  public static String getUserIpAddress() {
    return THREAD_LOCAL_IP_ADDRESS.get();
  }

  public static String getUserName() {
    return THREAD_LOCAL_USER_NAME.get();
  }
}
