/*
 * Copyright (c) 2012-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package org.dna.mqtt.moquette.messaging.spi.impl.subscriptions;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

import org.dna.mqtt.moquette.messaging.spi.impl.MemoryStorageService;
import org.dna.mqtt.moquette.proto.messages.AbstractMessage;
import org.fusesource.hawtdb.api.MultiIndexFactory;
import org.fusesource.hawtdb.api.PageFile;
import org.fusesource.hawtdb.api.PageFileFactory;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author andrea
 */
public class SubscriptionsStoreTest {
    
    private File hawtFile;
    private SubscriptionsStore store;

    public SubscriptionsStoreTest() {
    }

    @Before
    public void setUp() throws IOException {
        hawtFile = File.createTempFile("moquette_persistent_store", ".dat");
        
        store = new SubscriptionsStore();
//        store.init(hawtFile.getAbsolutePath());
        PageFileFactory pageFactory = new PageFileFactory();
        
        pageFactory.setFile(hawtFile);
        pageFactory.open();
        PageFile pageFile = pageFactory.getPageFile();
        MultiIndexFactory multiIndexFactory = new MultiIndexFactory(pageFile);
        store.init(new MemoryStorageService());
    }
    
    @After
    public void tearDown() {
        if (hawtFile.exists()) {
            hawtFile.delete();
        }
    }

    @Test
    public void testSplitTopic() throws ParseException {
        List tokens = store.splitTopic("finance/stock/ibm");
        assertEqualsSeq(asArray("finance", "stock", "ibm"), tokens);

        tokens = store.splitTopic("/finance/stock/ibm");
        assertEqualsSeq(asArray(Token.EMPTY, "finance", "stock", "ibm"), tokens);

        tokens = store.splitTopic("/");
        assertEqualsSeq(asArray(Token.EMPTY, Token.EMPTY), tokens);
    }

//    @Test(expected = ParseException.class)
//    public void testSplitTopicTwinsSlashAvoided() throws ParseException {
//        store.splitTopic("/finance//stock/ibm");
//    }

    @Test
    public void testSplitTopicMultiValid() throws ParseException {
        List tokens = store.splitTopic("finance/stock/#");
        assertEqualsSeq(asArray("finance", "stock", Token.MULTI), tokens);

        tokens = store.splitTopic("#");
        assertEqualsSeq(asArray(Token.MULTI), tokens);
    }

    @Test(expected = ParseException.class)
    public void testSplitTopicMultiInTheMiddleNotValid() throws ParseException {
        store.splitTopic("finance/#/closingprice");
    }

    @Test(expected = ParseException.class)
    public void testSplitTopicMultiNotAferSeparatorNotValid() throws ParseException {
        store.splitTopic("finance#");
    }

    @Test
    public void testSplitTopicSingleValid() throws ParseException {
        List tokens = store.splitTopic("finance/stock/+");
        assertEqualsSeq(asArray("finance", "stock", Token.SINGLE), tokens);

        tokens = store.splitTopic("+");
        assertEqualsSeq(asArray(Token.SINGLE), tokens);

        tokens = store.splitTopic("finance/+/ibm");
        assertEqualsSeq(asArray("finance", Token.SINGLE, "ibm"), tokens);
    }

    @Test(expected = ParseException.class)
    public void testSplitTopicSingleNotAferSeparatorNotValid() throws ParseException {
        store.splitTopic("finance+");
    }

    @Test
    public void testMatchSimple() {
        Subscription slashSub = new Subscription("FAKE_CLI_ID_1", "/", AbstractMessage.QOSType.MOST_ONE, false);
        store.add(slashSub);
        assertTrue(store.matches("finance").isEmpty());
        
        Subscription slashFinanceSub = new Subscription("FAKE_CLI_ID_1", "/finance", AbstractMessage.QOSType.MOST_ONE, false);
        store.add(slashFinanceSub);
        assertTrue(store.matches("finance").isEmpty());
        
        assertTrue(store.matches("/finance").contains(slashFinanceSub));
        assertTrue(store.matches("/").contains(slashSub));
    }
    
    @Test
    public void testMatchSimpleMulti() {
        Subscription anySub = new Subscription("FAKE_CLI_ID_1", "#", AbstractMessage.QOSType.MOST_ONE, false);
        store.add(anySub);
        assertTrue(store.matches("finance").contains(anySub));
        
        Subscription financeAnySub = new Subscription("FAKE_CLI_ID_1", "finance/#", AbstractMessage.QOSType.MOST_ONE, false);
        store.add(financeAnySub);
        assertTrue(store.matches("finance").containsAll(Arrays.asList(financeAnySub, anySub)));
    }
    
    @Test
    public void testMatchingDeepMulti_one_layer() {
        Subscription anySub = new Subscription("FAKE_CLI_ID_1", "#", AbstractMessage.QOSType.MOST_ONE, false);
        Subscription financeAnySub = new Subscription("FAKE_CLI_ID_1", "finance/#", AbstractMessage.QOSType.MOST_ONE, false);
        store.add(anySub);
        store.add(financeAnySub);
        
        //Verify
        assertTrue(store.matches("finance/stock").containsAll(Arrays.asList(financeAnySub, anySub)));
        assertTrue(store.matches("finance/stock/ibm").containsAll(Arrays.asList(financeAnySub, anySub)));
    }
    
    
    @Test
    public void testMatchingDeepMulti_two_layer() {
        Subscription financeAnySub = new Subscription("FAKE_CLI_ID_1", "finance/stock/#", AbstractMessage.QOSType.MOST_ONE, false);
        store.add(financeAnySub);
        
        //Verify
        assertTrue(store.matches("finance/stock/ibm").contains(financeAnySub));
    }
    
    @Test
    public void testMatchSimpleSingle() {
        Subscription anySub = new Subscription("FAKE_CLI_ID_1", "+", AbstractMessage.QOSType.MOST_ONE, false);
        store.add(anySub);
        assertTrue(store.matches("finance").contains(anySub));
        
        Subscription financeOne = new Subscription("FAKE_CLI_ID_1", "finance/+", AbstractMessage.QOSType.MOST_ONE, false);
        store.add(financeOne);
        assertTrue(store.matches("finance/stock").contains(financeOne));
    }
    
    @Test
    public void testMatchManySingle() {
        Subscription manySub = new Subscription("FAKE_CLI_ID_1", "+/+", AbstractMessage.QOSType.MOST_ONE, false);
        store.add(manySub);
        
        //verify
        assertTrue(store.matches("/finance").contains(manySub));
    }
    
    
    @Test
    public void testMatchSlashSingle() {
        Subscription slashPlusSub = new Subscription("FAKE_CLI_ID_1", "/+", AbstractMessage.QOSType.MOST_ONE, false);
        store.add(slashPlusSub);
        Subscription anySub = new Subscription("FAKE_CLI_ID_1", "+", AbstractMessage.QOSType.MOST_ONE, false);
        store.add(anySub);
        
        //Verify
        assertEquals(1, store.matches("/finance").size());
        assertTrue(store.matches("/finance").contains(slashPlusSub));
        assertFalse(store.matches("/finance").contains(anySub));
    }
    
    
    @Test
    public void testMatchManyDeepSingle() {
        Subscription slashPlusSub = new Subscription("FAKE_CLI_ID_1", "/finance/+/ibm", AbstractMessage.QOSType.MOST_ONE, false);
        store.add(slashPlusSub);
        
        Subscription slashPlusDeepSub = new Subscription("FAKE_CLI_ID_1", "/+/stock/+", AbstractMessage.QOSType.MOST_ONE, false);
        store.add(slashPlusDeepSub);
        
        //Verify
        assertTrue(store.matches("/finance/stock/ibm").containsAll(Arrays.asList(slashPlusSub, slashPlusDeepSub)));
    }

    @Test
    public void testMatchSimpleMulti_allTheTree() {
        store.add(new Subscription("FAKE_CLI_ID_1", "#", AbstractMessage.QOSType.MOST_ONE, false));
        assertFalse(store.matches("finance").isEmpty());
        assertFalse(store.matches("finance/ibm").isEmpty());
    }

    @Test
    public void testMatchSimpleMulti_zeroLevel() {
        //check  MULTI in case of zero level match
        store.add(new Subscription("FAKE_CLI_ID_1", "finance/#", AbstractMessage.QOSType.MOST_ONE, false));
        assertFalse(store.matches("finance").isEmpty());
    }
    
    @Test
    public void rogerLightTopicMatches() {
        assertMatch("foo/bar", "foo/bar");
        assertMatch("foo/bar", "foo/bar");
        assertMatch("foo/+", "foo/bar");
        assertMatch("foo/+/baz", "foo/bar/baz");
        assertMatch("foo/+/#", "foo/bar/baz");
        assertMatch("#", "foo/bar/baz");

        assertNotMatch("foo/bar", "foo");
        assertNotMatch("foo/+", "foo/bar/baz");
        assertNotMatch("foo/+/baz", "foo/bar/bar");
        assertNotMatch("foo/+/#", "fo2/bar/baz");

        assertMatch("#", "/foo/bar");
        assertMatch("/#", "/foo/bar");
        assertNotMatch("/#", "foo/bar");

        assertMatch("foo//bar", "foo//bar");
        assertMatch("foo//+", "foo//bar");
        assertMatch("foo/+/+/baz", "foo///baz");
        assertMatch("foo/bar/+", "foo/bar/");
    }
    
    private void assertMatch(String subscription, String topic) {
        store = new SubscriptionsStore();
        store.init(new MemoryStorageService());
        Subscription sub = new Subscription("FAKE_CLI_ID_1", subscription, AbstractMessage.QOSType.MOST_ONE, false);
        store.add(sub);
        assertFalse(store.matches(topic).isEmpty());
    }
    
    private void assertNotMatch(String subscription, String topic) {
        store = new SubscriptionsStore();
        store.init(new MemoryStorageService());
        Subscription sub = new Subscription("FAKE_CLI_ID_1", subscription, AbstractMessage.QOSType.MOST_ONE, false);
        store.add(sub);
        assertTrue(store.matches(topic).isEmpty());
    }
    
    
    @Test
    public void testRemoveClientSubscriptions_existingClientID() {
        String cliendID = "FAKE_CLID_1";
        store.add(new Subscription(cliendID, "finance/#", AbstractMessage.QOSType.MOST_ONE, false));
        
        //Exercise
        store.removeForClient(cliendID);
        
        //Verify
        assertEquals(0, store.size());
    }
    
    @Test
    public void testRemoveClientSubscriptions_notexistingClientID() {
        String cliendID = "FAKE_CLID_1";
        store.add(new Subscription(cliendID, "finance/#", AbstractMessage.QOSType.MOST_ONE, false));
        
        //Exercise
        store.removeForClient("FAKE_CLID_2");
        
        //Verify
        assertEquals(1, store.size());
    }

    @Test
    public void testMatchTopics_simple() {
        assertTrue(SubscriptionsStore.matchTopics("/", "/"));
        assertTrue(SubscriptionsStore.matchTopics("/finance", "/finance"));
    }

    @Test
    public void testMatchTopics_multi() {
        assertTrue(SubscriptionsStore.matchTopics("finance", "#"));
        assertTrue(SubscriptionsStore.matchTopics("finance", "finance/#"));
        assertTrue(SubscriptionsStore.matchTopics("finance/stock", "finance/#"));
        assertTrue(SubscriptionsStore.matchTopics("finance/stock/ibm", "finance/#"));
    }


    @Test
    public void testMatchTopics_single() {
        assertTrue(SubscriptionsStore.matchTopics("finance", "+"));
        assertTrue(SubscriptionsStore.matchTopics("finance/stock", "finance/+"));
        assertFalse(SubscriptionsStore.matchTopics("finance", "finance/+"));
        assertTrue(SubscriptionsStore.matchTopics("/finance", "/+"));
        assertFalse(SubscriptionsStore.matchTopics("/finance", "+"));
        assertTrue(SubscriptionsStore.matchTopics("/finance", "+/+"));
        assertTrue(SubscriptionsStore.matchTopics("/finance/stock/ibm", "/finance/+/ibm"));
        assertTrue(SubscriptionsStore.matchTopics("/", "+/+"));
        assertFalse(SubscriptionsStore.matchTopics("/finance/stock", "+"));
    }
    
    @Test
    public void rogerLightMatchTopics() {
        assertTrue(SubscriptionsStore.matchTopics("foo/bar", "foo/bar"));
        assertTrue(SubscriptionsStore.matchTopics("foo/bar", "foo/+"));
        assertTrue(SubscriptionsStore.matchTopics("foo/bar/baz", "foo/+/baz"));
        assertTrue(SubscriptionsStore.matchTopics("foo/bar/baz", "foo/+/#"));
        assertTrue(SubscriptionsStore.matchTopics("foo/bar/baz", "#"));
        
        assertFalse(SubscriptionsStore.matchTopics("foo", "foo/bar"));
        assertFalse(SubscriptionsStore.matchTopics("foo/bar/baz", "foo/+"));
        assertFalse(SubscriptionsStore.matchTopics("foo/bar/bar", "foo/+/baz"));
        assertFalse(SubscriptionsStore.matchTopics("fo2/bar/baz", "foo/+/#"));
        
        assertTrue(SubscriptionsStore.matchTopics("/foo/bar", "#"));
        assertTrue(SubscriptionsStore.matchTopics("/foo/bar", "/#"));
        assertFalse(SubscriptionsStore.matchTopics("foo/bar", "/#"));
        
        assertTrue(SubscriptionsStore.matchTopics("foo//bar", "foo//bar"));
        assertTrue(SubscriptionsStore.matchTopics("foo//bar", "foo//+"));
        assertTrue(SubscriptionsStore.matchTopics("foo///baz", "foo/+/+/baz"));
        assertTrue(SubscriptionsStore.matchTopics("foo/bar/", "foo/bar/+"));
    }
    
    @Test
    public void removeSubscription_withDifferentClients_subscribedSameTopic() {
        SubscriptionsStore aStore = new SubscriptionsStore();
        aStore.init(new MemoryStorageService());
        //subscribe a not active clientID1 to /topic
        Subscription slashSub = new Subscription("FAKE_CLI_ID_1", "/topic", AbstractMessage.QOSType.MOST_ONE, false);
        aStore.add(slashSub);
        aStore.deactivate(slashSub.getClientId());
        
        //subscribe an active clientID2 to /topic
        Subscription slashSub2 = new Subscription("FAKE_CLI_ID_2", "/topic", AbstractMessage.QOSType.MOST_ONE, false);
        aStore.add(slashSub2);
        
        //Exercise
        aStore.removeSubscription("/topic", slashSub2.getClientId());
        
        //Verify
        Subscription remainedSubscription = aStore.matches("/topic").get(0);
        assertEquals(slashSub.getClientId(), remainedSubscription.getClientId());
    }
    
    @Test
    public void overridingSubscriptions() {
        Subscription oldSubscription = new Subscription("FAKE_CLI_ID_1", "/topic", AbstractMessage.QOSType.MOST_ONE, false);
        store.add(oldSubscription);
        Subscription overrindingSubscription = new Subscription("FAKE_CLI_ID_1", "/topic", AbstractMessage.QOSType.EXACTLY_ONCE, false);
        store.add(overrindingSubscription);
        
        //Verify
        List<Subscription> subscriptions = store.matches("/topic");
        assertEquals(1, subscriptions.size());
        Subscription sub = subscriptions.get(0);
        assertEquals(overrindingSubscription.getRequestedQos(), sub.getRequestedQos());
    }

    private static Token[] asArray(Object... l) {
        Token[] tokens = new Token[l.length];
        for (int i = 0; i < l.length; i++) {
            Object o = l[i];
            if (o instanceof Token) {
                tokens[i] = (Token) o;
            } else {
                tokens[i] = new Token(o.toString());
            }
        }

        return tokens;
    }

    private void assertEqualsSeq(Token[] exptected, List<Token> result) {
        List<Token> expectedList = Arrays.asList(exptected);
        assertEquals(expectedList, result);
    }
}
