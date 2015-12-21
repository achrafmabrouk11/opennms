package org.opennms.netmgt.jasper.jrobin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import net.sf.jasperreports.engine.JRException;

import org.jrobin.core.RrdException;
import org.junit.Test;


public class RrdXportCmdTest {
    
    @Test
    public void testExecute() throws RrdException, IOException, JRException {
         JRobinDataSource dataSource = (JRobinDataSource) new RrdXportCmd().executeCommand(getQueryString());
         assertTrue(dataSource.next());
    }
    


    private String getQueryString() {
        return "--start 1287005100 --end 1287018990 DEF:xx=src/test/resources/http-8980.jrb:http-8980:AVERAGE DEF:zz=src/test/resources/ssh.jrb:ssh:AVERAGE XPORT:xx:HttpLatency XPORT:zz:SshLatency";
    }
    
    private String getWindowsString() {
        return "--start 1287005100 --end 1287018990 DEF:xx=\\jrbs\\http-8980.jrb:http-8980:AVERAGE DEF:zz=\\jrbs\\ssh.jrb:ssh:AVERAGE XPORT:xx:HttpLatency XPORT:zz:SshLatency";
    }
    
    @Test
    public void testColonSplitter() throws RrdException {
        String[] splitter = new ColonSplitter("DEF:xx=C\\:\\jrbs\\http-8980.jrb:http-8980:AVERAGE").split();
        assertEquals(4, splitter.length);
    }
}