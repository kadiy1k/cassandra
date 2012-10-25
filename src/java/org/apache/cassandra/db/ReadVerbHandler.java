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
package org.apache.cassandra.db;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.net.IVerbHandler;
import org.apache.cassandra.net.MessageIn;
import org.apache.cassandra.net.MessageOut;
import org.apache.cassandra.net.MessagingService;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.utils.ByteBufferUtil;

public class ReadVerbHandler implements IVerbHandler<ReadCommand>
{
    private static final Logger logger = LoggerFactory.getLogger( ReadVerbHandler.class );

    public void doVerb(MessageIn<ReadCommand> message, String id)
    {
        if (StorageService.instance.isBootstrapMode())
        {
            throw new RuntimeException("Cannot service reads while bootstrapping!");
        }

        try
        {
            ReadCommand command = message.payload;
            Table table = Table.open(command.table);
            Row row = command.getRow(table);

            MessageOut<ReadResponse> reply = new MessageOut<ReadResponse>(MessagingService.Verb.REQUEST_RESPONSE,
                                                                          getResponse(command, row),
                                                                          ReadResponse.serializer);
            logger.debug("Enqueuing response to {}", message.from);
            MessagingService.instance().sendReply(reply, id, message.from);
        }
        catch (IOException ex)
        {
            throw new RuntimeException(ex);
        }
    }

    public static ReadResponse getResponse(ReadCommand command, Row row)
    {
        if (command.isDigestQuery())
        {
            if (logger.isTraceEnabled())
                logger.trace("digest is " + ByteBufferUtil.bytesToHex(ColumnFamily.digest(row.cf)));
            return new ReadResponse(ColumnFamily.digest(row.cf));
        }
        else
        {
            return new ReadResponse(row);
        }
    }
}
