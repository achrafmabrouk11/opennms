//
// This file is part of the OpenNMS(R) Application.
//
// OpenNMS(R) is Copyright (C) 2005 The OpenNMS Group, Inc.  All rights reserved.
// OpenNMS(R) is a derivative work, containing both original code, included code and modified
// code that was published under the GNU General Public License. Copyrights for modified 
// and included code are below.
//
// OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
//
// Modifications:
//
// 2007 Jun 23: Code formatting, wrap debug log messages that aren't
//              simple strings in isDebugEnabled() tests, and SNMP
//              version string, session creation, and PDU creation
//              have moved into Snmp4JAgentConfig. - dj@opennms.org
//
// Original code base Copyright (C) 1999-2001 Oculan Corp.  All rights reserved.
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
//
// For more information contact:
// OpenNMS Licensing       <license@opennms.org>
//     http://www.opennms.org/
//     http://www.opennms.com/
//
package org.opennms.netmgt.snmp.snmp4j;

import java.io.IOException;

import org.opennms.netmgt.snmp.CollectionTracker;
import org.opennms.netmgt.snmp.PduBuilder;
import org.opennms.netmgt.snmp.SnmpGetter;
import org.opennms.netmgt.snmp.SnmpObjId;
import org.opennms.netmgt.snmp.SnmpValue;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.event.ResponseListener;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;

public class Snmp4JGetter extends SnmpGetter {

    public static abstract class Snmp4JPduBuilder extends PduBuilder {
        public Snmp4JPduBuilder(int maxVarsPerPdu) {
            super(maxVarsPerPdu);
        }
        
        public abstract PDU getPdu();
    }
    
    public class GetBuilder extends Snmp4JPduBuilder {

        private PDU m_getPdu = null;

        private GetBuilder(int maxVarsPerPdu) {
            super(maxVarsPerPdu);
            reset();
        }
        
        public void reset() {
            m_getPdu = m_agentConfig.createPdu(PDU.GET);
        }

        public PDU getPdu() {
            return m_getPdu;
        }
        
        public void addOid(SnmpObjId snmpObjId) {
            VariableBinding varBind = new VariableBinding(new OID(snmpObjId.getIds()));
            m_getPdu.add(varBind);
        }

        public void setNonRepeaters(int numNonRepeaters) {
        }

        public void setMaxRepetitions(int maxRepititions) {
        }
        
    }
    
    /**
     * TODO: Merge this logic with {@link Snmp4JStrategy#processResponse()}
     */
    public class Snmp4JResponseListener implements ResponseListener {

        private void processResponse(PDU response) {
            try {
                if (log().isDebugEnabled()) {
                    log().debug("Received a tracker PDU of type "+PDU.getTypeString(response.getType())+" from "+getAddress()+" of size "+response.size()+", errorStatus = "+response.getErrorStatus()+", errorStatusText = "+response.getErrorStatusText()+", errorIndex = "+response.getErrorIndex());
                }
                if (response.getType() == PDU.REPORT) {
                    handleAuthError("A REPORT PDU was returned from the agent.  This is most likely an authentication problem.  Please check the config");
                } else {
                    if (!processErrors(response.getErrorStatus(), response.getErrorIndex())) {
                        for (int i = 0; i < response.size(); i++) {
                            VariableBinding vb = response.get(i);
                            SnmpObjId receivedOid = SnmpObjId.get(vb.getOid().getValue());
                            SnmpValue val = new Snmp4JValue(vb.getVariable());
                            Snmp4JGetter.this.processResponse(receivedOid, val);
                        }
                    }
                    buildAndSendPdu();
                }
            } catch (Throwable e) {
                handleFatalError(e);
            }
        }

        public void onResponse(ResponseEvent responseEvent) {
            // need to cancel the request here otherwise SNMP4J Keeps it around forever... go figure
            m_session.cancel(responseEvent.getRequest(), this);

            // Check to see if we got an interrupted exception
            if (responseEvent.getError() instanceof InterruptedException) {
                if (log().isDebugEnabled()) {
                    log().debug("Interruption event.  We have probably tried to close the session due to an error: " + responseEvent.getError(), responseEvent.getError());
                }
            // Check to see if the response is null, indicating a timeout
            } else if (responseEvent.getResponse() == null) {
                handleTimeout(getName()+": snmpTimeoutError for: " + getAddress());
            // Check to see if we got any kind of error
            } else if (responseEvent.getError() != null){
                handleError(getName()+": snmpInternalError: " + responseEvent.getError() + " for: " + getAddress(), responseEvent.getError());
            // If we have a PDU in the response, process it
            } else {
                processResponse(responseEvent.getResponse());
            }
            
        }
        
        
    }
    
    private Snmp m_session;
    private final Target m_tgt;
    private final ResponseListener m_listener;
    private final Snmp4JAgentConfig m_agentConfig;

    public Snmp4JGetter(Snmp4JAgentConfig agentConfig, String name, CollectionTracker tracker) {
        super(agentConfig.getInetAddress(), name, agentConfig.getMaxVarsPerPdu(), agentConfig.getMaxRepetitions(), tracker);
        
        m_agentConfig = agentConfig;
        
        m_tgt = agentConfig.getTarget();
        m_listener = new Snmp4JResponseListener();
    }
    
    public void start() {
        
        if (log().isDebugEnabled()) {
            log().info("get "+getName()+" for "+getAddress()+" using version "+m_agentConfig.getVersionString()+" with config: "+m_agentConfig);
        }
            
        super.start();
    }

    protected PduBuilder createPduBuilder(int maxVarsPerPdu) {
        return  (PduBuilder)new GetBuilder(maxVarsPerPdu);
    }

    protected void sendPdu(PduBuilder pduBuilder) throws IOException {
        Snmp4JPduBuilder snmp4JPduBuilder = (Snmp4JPduBuilder)pduBuilder;
        if (m_session == null) {
            m_session = m_agentConfig.createSnmpSession();
            m_session.listen();
        }
        
        if (log().isDebugEnabled()) {
            log().debug("Sending tracker pdu of size "+snmp4JPduBuilder.getPdu().size());
        }
        m_session.send(snmp4JPduBuilder.getPdu(), m_tgt, null, m_listener);
    }
    
    protected int getVersion() {
        return m_tgt.getVersion();
    }

    protected void close() throws IOException {
        if (m_session != null) {
            m_session.close();
            m_session = null;
        }
    }
}