package com.globo.globodns.cloudstack.resource;


import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import com.cloud.agent.api.Answer;
import com.cloud.utils.component.ComponentContext;
import com.globo.globodns.client.GloboDns;
import com.globo.globodns.client.api.DomainAPI;
import com.globo.globodns.client.api.ExportAPI;
import com.globo.globodns.client.api.RecordAPI;
import com.globo.globodns.client.model.Domain;
import com.globo.globodns.client.model.Record;
import com.globo.globodns.cloudstack.commands.CreateOrUpdateRecordAndReverseCommand;
import com.globo.globodns.cloudstack.commands.RemoveDomainCommand;
import com.globo.globodns.cloudstack.commands.RemoveRecordCommand;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class)
@DirtiesContext(classMode=ClassMode.AFTER_EACH_TEST_METHOD)
@SuppressWarnings("unused")
public class GloboDnsResourceTest {
	
	private GloboDnsResource _globoDnsResource;
	
	private GloboDns _globoDnsApi;
	private DomainAPI _domainApi;
	private RecordAPI _recordApi;
	private ExportAPI _exportApi;
	
	private static final Long TEMPLATE_ID = 1l;
	
	private static long sequenceId = 10l;

	@Before
	public void setUp() throws Exception {
        // ComponentContext.initComponentsLifeCycle();
        
        String name = "GloboDNS";
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("zoneId", "1");
        params.put("guid", "globodns");
        params.put("name", name);
        params.put("url", "http://example.com");
        params.put("username", "username");
        params.put("password", "password");
        
        _globoDnsResource = new GloboDnsResource();
        _globoDnsResource.configure(name, params);
        
        _globoDnsApi = spy(_globoDnsResource._globoDns);
        _globoDnsResource._globoDns = _globoDnsApi;
        
        _domainApi = mock(DomainAPI.class);
        when(_globoDnsApi.getDomainAPI()).thenReturn(_domainApi);
        
        _recordApi = mock(RecordAPI.class);
        when(_globoDnsApi.getRecordAPI()).thenReturn(_recordApi);
        
        _exportApi = mock(ExportAPI.class);
        when(_globoDnsApi.getExportAPI()).thenReturn(_exportApi);
    }
    
    @After
	public void tearDown() throws Exception {
    }
    
    private Domain generateFakeDomain(String domainName, boolean reverse) {
    	Domain domain = new Domain();
    	domain.getDomainAttributes().setId(sequenceId++);
    	domain.getDomainAttributes().setName(domainName);
    	List<Domain> domainList = new ArrayList<Domain>();
    	domainList.add(domain);
    	if (reverse) {
        	when(_domainApi.listReverseByQuery(eq(domainName))).thenReturn(domainList);
    	} else {
        	when(_domainApi.listByQuery(eq(domainName))).thenReturn(domainList);
    	}
    	return domain;
    }
    
    private Record generateFakeRecord(Domain domain, String recordName, String recordContent, boolean reverse) {
    	Record record = new Record();
//    	String recordType;
    	if (reverse) {
//    		recordType = "PTR";
        	record.getTypePTRRecordAttributes().setName(recordName);
        	record.getTypePTRRecordAttributes().setContent(recordContent);
        	record.getTypePTRRecordAttributes().setDomainId(domain.getId());
        	record.getTypePTRRecordAttributes().setId(sequenceId++);
    	} else {
//    		recordType = "A";
        	record.getTypeARecordAttributes().setName(recordName);
        	record.getTypeARecordAttributes().setContent(recordContent);
        	record.getTypeARecordAttributes().setDomainId(domain.getId());
        	record.getTypeARecordAttributes().setId(sequenceId++);
    	}
    	List<Record> recordList = new ArrayList<Record>();
    	recordList.add(record);
    	when(_recordApi.listByQuery(eq(domain.getId()), eq(recordName))).thenReturn(recordList);
    	return record;
    }
    
    @Test
    public void testCreateRecordAndReverseWillSuccessWhenDomainExistsAndRecordNotAndOverrideIsTrue() throws Exception {
    	String recordName = "recordname";
    	String recordIp = "10.10.10.10";
    	String domainName = "domain.name.com";
    	String reverseDomainName = "10.10.10.in-addr.arpa";
    	String reverseRecordName = "10";
    	String reverseRecordContent = recordName + "." + domainName;
    	
    	Domain domain = generateFakeDomain(domainName, false);
    	Record record = generateFakeRecord(domain, recordName, recordIp, false);
    	Domain reverseDomain = generateFakeDomain(reverseDomainName, true);
    	Record reverseRecord = generateFakeRecord(reverseDomain, reverseRecordName, reverseRecordContent, true);

    	when(_recordApi.createRecord(eq(domain.getId()), eq(recordName), eq(recordIp), eq("A"))).thenReturn(record);
    	when(_recordApi.createRecord(eq(reverseDomain.getId()), eq(reverseRecordName), eq(reverseRecordContent), eq("PTR"))).thenReturn(record);

    	Answer answer = _globoDnsResource.execute(new CreateOrUpdateRecordAndReverseCommand(recordName, recordIp, domainName, TEMPLATE_ID, true));
    	assertNotNull(answer);
    	assertEquals(true, answer.getResult());
    	verify(_exportApi, times(1)).scheduleExport();
    }

    @Test
    public void testCreateRecordAndReverseWillFailWhenRecordAlreadyExistsAndOverrideIsFalse() throws Exception {
    	String recordName = "recordname";
    	String newIp = "10.10.10.10";
    	String oldIp = "20.20.20.20";
    	String domainName = "domain.name.com";

    	Domain domain = generateFakeDomain(domainName, false);
    	Record record = generateFakeRecord(domain, recordName, oldIp, false);

    	Answer answer = _globoDnsResource.execute(new CreateOrUpdateRecordAndReverseCommand(recordName, newIp, domainName, TEMPLATE_ID, false));
    	assertNotNull(answer);
    	assertEquals(false, answer.getResult());
    }

	@Test
    public void testCreateRecordAndReverseWillFailWhenReverseRecordAlreadyExistsAndOverrideIsFalse() throws Exception {
    	String recordName = "recordname";
    	String recordIp = "10.10.10.10";
    	String domainName = "domain.name.com";
    	String reverseDomainName = "10.10.10.in-addr.arpa";
    	String reverseRecordName = "10";
    	
    	Domain domain = generateFakeDomain(domainName, false);
    	Record record = generateFakeRecord(domain, recordName, recordIp, false);
    	Domain reverseDomain = generateFakeDomain(reverseDomainName, true);
    	Record reverseRecord = generateFakeRecord(reverseDomain, reverseRecordName, "X", true);

    	Answer answer = _globoDnsResource.execute(new CreateOrUpdateRecordAndReverseCommand(recordName, recordIp, domainName, TEMPLATE_ID, false));
    	assertNotNull(answer);
    	assertEquals(false, answer.getResult());
    }

    @Test
    public void testCreateRecordAndReverseWhenDomainDoesNotExist() throws Exception {
    	String recordName = "recordname";
    	String recordIp = "10.10.10.10";
    	String domainName = "domain.name.com";
    	
    	when(_domainApi.listByQuery(domainName)).thenReturn(new ArrayList<Domain>());

    	Answer answer = _globoDnsResource.execute(new CreateOrUpdateRecordAndReverseCommand(recordName, recordIp, domainName, TEMPLATE_ID, true));
    	assertNotNull(answer);
    	assertEquals("Domain " + domainName + " doesn't exist in GloboDns", answer.getDetails());
    	assertEquals(false, answer.getResult());
    	verify(_exportApi, never()).scheduleExport();
    }

    @Test
    public void testUpdateRecordAndReverseWhenDomainExistsAndOverrideIsTrue() throws Exception {
    	String recordName = "recordname";
    	String oldRecordIp = "10.10.10.10";
    	String newRecordIp = "20.20.20.20";
    	String domainName = "domain.name.com";
    	String reverseDomainName = "20.20.20.in-addr.arpa";
    	String reverseRecordName = "20";
    	String reverseRecordContent = recordName + "." + domainName;
    	
    	Domain domain = generateFakeDomain(domainName, false);
    	Record record = generateFakeRecord(domain, recordName, oldRecordIp, false);
    	Domain reverseDomain = generateFakeDomain(reverseDomainName, true);
    	Record reverseRecord = generateFakeRecord(reverseDomain, reverseRecordName, "X", true);

    	Answer answer = _globoDnsResource.execute(new CreateOrUpdateRecordAndReverseCommand(recordName, newRecordIp, domainName, TEMPLATE_ID, true));
    	
    	// ensure calls in sequence to ensure this call are the only ones.
    	InOrder inOrder = inOrder(_recordApi);
    	inOrder.verify(_recordApi, times(1)).updateRecord(eq(record.getId()), eq(domain.getId()), eq(recordName), eq(newRecordIp));
    	inOrder.verify(_recordApi, times(1)).updateRecord(eq(reverseRecord.getId()), eq(reverseDomain.getId()), eq(reverseRecordName), eq(reverseRecordContent));

    	assertNotNull(answer);
    	assertEquals(true, answer.getResult());
    	verify(_exportApi, times(1)).scheduleExport();
    }

    @Test
    public void testRemoveRecordWhenRecordExists() throws Exception {
    	String recordName = "recordname";
    	String recordIp = "10.10.10.10";
    	String domainName = "domain.name.com";
    	String reverseDomainName = "10.10.10.in-addr.arpa";
    	String reverseRecordName = "10";
    	String reverseRecordContent = recordName + "." + domainName;
    	
    	Domain domain = generateFakeDomain(domainName, false);
    	Record record = generateFakeRecord(domain, recordName, recordIp, false);
    	Domain reverseDomain = generateFakeDomain(reverseDomainName, true);
    	Record reverseRecord = generateFakeRecord(reverseDomain, reverseRecordName, reverseRecordContent, true);

    	Answer answer = _globoDnsResource.execute(new RemoveRecordCommand(recordName, recordIp, domainName, true));
    	
    	assertNotNull(answer);
    	assertEquals(true, answer.getResult());
    	verify(_recordApi, times(1)).removeRecord(eq(record.getId()));
    	verify(_recordApi, times(1)).removeRecord(eq(reverseRecord.getId()));
    	verify(_exportApi, times(1)).scheduleExport();
    }

    @Test
    public void testRemoveRecordCommandWhenReverseRecordExistsWithDifferentValueAndOverrideIsFalseCommandWillSuccessButReverseRecordIsNotRemove() throws Exception {
    	String recordName = "recordname";
    	String recordIp = "10.10.10.10";
    	String domainName = "domain.name.com";
    	String reverseDomainName = "10.10.10.in-addr.arpa";
    	String reverseRecordName = "10";
    	String reverseRecordContent = recordName + "." + domainName;
    	
    	Domain domain = generateFakeDomain(domainName, false);
    	Record record = generateFakeRecord(domain, recordName, recordIp, false);
    	Domain reverseDomain = generateFakeDomain(reverseDomainName, true);
    	Record reverseRecord = generateFakeRecord(reverseDomain, reverseRecordName, "X", true);

    	Answer answer = _globoDnsResource.execute(new RemoveRecordCommand(recordName, recordIp, domainName, false));
    	
    	assertEquals(true, answer.getResult());
    	verify(_recordApi, times(1)).removeRecord(eq(record.getId()));
    	verify(_recordApi, never()).removeRecord(eq(reverseRecord.getId()));
    	verify(_exportApi, times(1)).scheduleExport();
    }

    @Test
    public void testRemoveRecordCommandWhenRecordExistsWithDifferentValueAndOverrideIsFalseCommandWillSuccessButRecordIsNotRemoveButReverseWill() throws Exception {
    	String recordName = "recordname";
    	String recordIp = "10.10.10.10";
    	String domainName = "domain.name.com";
    	String reverseDomainName = "10.10.10.in-addr.arpa";
    	String reverseRecordName = "10";
    	String reverseRecordContent = recordName + "." + domainName;
    	
    	Domain domain = generateFakeDomain(domainName, false);
    	Record record = generateFakeRecord(domain, recordName, "X", false);
    	Domain reverseDomain = generateFakeDomain(reverseDomainName, true);
    	Record reverseRecord = generateFakeRecord(reverseDomain, reverseRecordName, reverseRecordContent, true);

    	Answer answer = _globoDnsResource.execute(new RemoveRecordCommand(recordName, recordIp, domainName, false));
    	
    	assertEquals(true, answer.getResult());
    	verify(_recordApi, never()).removeRecord(eq(record.getId()));
    	verify(_recordApi, times(1)).removeRecord(eq(reverseRecord.getId()));
    	verify(_exportApi, times(1)).scheduleExport();
    }

    @Test
    public void testRemoveDomainCommandWhenDomainExistsAndThereAreRecordsAndOverrideIsFalseCommandWillSuccessButDomainAreKeeped() throws Exception {
    	String recordName = "recordname";
    	String recordIp = "10.10.10.10";
    	String domainName = "domain.name.com";
    	String reverseDomainName = "10.10.10.in-addr.arpa";
    	String reverseRecordName = "10";
    	String reverseRecordContent = recordName + "." + domainName;
    	
    	Domain domain = generateFakeDomain(domainName, false);
    	Record record = generateFakeRecord(domain, recordName, "X", false);
    	when(_recordApi.listAll(domain.getId())).thenReturn(Arrays.asList(record));

    	Answer answer = _globoDnsResource.execute(new RemoveRecordCommand(recordName, recordIp, domainName, false));
    	
    	assertEquals(true, answer.getResult());
    	verify(_domainApi, never()).removeDomain(any(Long.class));
    	verify(_exportApi, never()).scheduleExport();
    }

    @Test
    public void testRemoveDomainCommandWhenDomainExistsAndThereAreOnlyRecordsNSAndOverrideIsFalseCommandWillSuccess() throws Exception {
    	String recordName = "recordname";
    	String recordIp = "10.10.10.10";
    	String domainName = "domain.name.com";
    	String reverseDomainName = "10.10.10.in-addr.arpa";
    	String reverseRecordName = "10";
    	String reverseRecordContent = recordName + "." + domainName;
    	
    	Domain domain = generateFakeDomain(domainName, false);
    	List<Record> recordList = new ArrayList<Record>();
    	for (int i=0; i<10; i++) {
    		Record record = new Record();
    		record.getTypeNSRecordAttributes().setDomainId(domain.getId());
    		record.getTypeNSRecordAttributes().setId(sequenceId++);
    		record.getTypeNSRecordAttributes().setType("NS");
    		recordList.add(record);
    	}
    	when(_recordApi.listAll(domain.getId())).thenReturn(recordList);

    	Answer answer = _globoDnsResource.execute(new RemoveDomainCommand(domainName, false));
    	
    	assertEquals(true, answer.getResult());
    	verify(_domainApi, times(1)).removeDomain(eq(domain.getId()));
    	verify(_exportApi, times(1)).scheduleExport();
    }

}
