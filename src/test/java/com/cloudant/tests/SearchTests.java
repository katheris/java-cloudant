/*
 * Copyright (c) 2015 IBM Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.cloudant.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.cloudant.client.api.CloudantClient;
import com.cloudant.client.api.Database;
import com.cloudant.client.api.DesignDocumentManager;
import com.cloudant.client.api.Search;
import com.cloudant.client.api.model.DesignDocument;
import com.cloudant.client.api.model.SearchResult;
import com.cloudant.client.api.model.SearchResult.SearchResultRow;
import com.cloudant.client.org.lightcouch.internal.URIBuilder;
import com.cloudant.test.main.RequiresCloudant;
import com.cloudant.tests.util.CloudantClientResource;
import com.cloudant.tests.util.DatabaseResource;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.RuleChain;

import java.io.File;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@Category(RequiresCloudant.class)
public class SearchTests {

    public static CloudantClientResource clientResource = new CloudantClientResource();
    public static DatabaseResource dbResource = new DatabaseResource(clientResource);

    @ClassRule
    public static RuleChain chain = RuleChain.outerRule(clientResource).around(dbResource);

    private static Database db;
    private static CloudantClient account;

    @BeforeClass
    public static void setUp() throws Exception {
        account = clientResource.get();
        db = dbResource.get();

        // replciate the animals db for search tests
        com.cloudant.client.api.Replication r = account.replication();
        r.source("https://clientlibs-test.cloudant.com/animaldb");
        r.createTarget(true);
        r.target(dbResource.getDbURIWithUserInfo());
        r.trigger();

        // sync the design doc for faceted search
        File designDocViews101 =
                new File(String.format("%s/views101_design_doc.js", new File(System.getProperty(
                        "user.dir") + "/src/test/resources/design-files")));
        DesignDocument designDoc = DesignDocumentManager.fromFile(designDocViews101);
        db.getDesignDocumentManager().put(designDoc);
    }

    @Test
    public void searchCountsTest() {

        // do a faceted search for counts
        Search srch = db.search("views101/animals");
        SearchResult<Animal> rslt = srch.limit(10)
                .includeDocs(true)
                .counts(new String[]{"class", "diet"})
                .querySearchResult("l*", Animal.class);
        assertNotNull(rslt);
        assertNotNull(rslt.getCounts());
        assert (rslt.getCounts().keySet().size() == 2);
        assertEquals(rslt.getCounts().get("class").keySet().size(), 1);
        assertEquals(rslt.getCounts().get("class").get("mammal"), new Long(2));
        assertEquals(rslt.getCounts().get("diet").keySet().size(), 2);
        assertEquals(rslt.getCounts().get("diet").get("herbivore"), new Long(1));
        assertEquals(rslt.getCounts().get("diet").get("omnivore"), new Long(1));
        assertNotNull(rslt.getBookmark());
        assertEquals(rslt.getGroups().size(), 0);
        assertEquals(rslt.getRows().size(), 2);
        for (@SuppressWarnings("rawtypes") SearchResultRow r : rslt.getRows()) {
            assertNotNull(r.getDoc());
            assertNotNull(r.getFields());
            assertNotNull(r.getId());
            assertNotNull(r.getOrder());
        }

    }

    @Test
    public void rowsTest() {
        Search srch = db.search("views101/animals");
        List<Animal> animals = srch.limit(10)
                .includeDocs(true)
                .counts(new String[]{"class", "diet"})
                .query("l*", Animal.class);
        assertNotNull(animals);
        assertEquals(animals.size(), 2);
        for (Animal a : animals) {
            assertNotNull(a);
        }
    }

    @Test
    public void groupsTest() {
        Search srch = db.search("views101/animals");
        Map<String, List<Animal>> groups = srch.limit(10)
                .includeDocs(true)
                .counts(new String[]{"class", "diet"})
                .groupField("class", false)
                .queryGroups("l*", Animal.class);
        assertNotNull(groups);
        assertEquals(groups.size(), 1);
        for (Entry<String, List<Animal>> g : groups.entrySet()) {
            assertNotNull(g.getKey());
            assertEquals(g.getValue().size(), 2);
            for (Animal a : g.getValue()) {
                assertNotNull(a);
            }
        }
    }

    @Test
    public void rangesTest() {
        // do a faceted search for ranges
        Search srch = db.search("views101/animals");
        SearchResult<Animal> rslt = srch.includeDocs(true)
                .counts(new String[]{"class", "diet"})
                .ranges("{ \"min_length\": {\"small\": \"[0 TO 1.0]\","
                        + "\"medium\": \"[1.1 TO 3.0]\", \"large\": \"[3.1 TO 9999999]\"} }")
                .querySearchResult("class:mammal", Animal.class);
        assertNotNull(rslt);
        assertNotNull(rslt.getRanges());
        assertEquals(rslt.getRanges().entrySet().size(), 1);
        assertEquals(rslt.getRanges().get("min_length").entrySet().size(), 3);
        assertEquals(rslt.getRanges().get("min_length").get("small"), new Long(3));
        assertEquals(rslt.getRanges().get("min_length").get("medium"), new Long(3));
        assertEquals(rslt.getRanges().get("min_length").get("large"), new Long(2));

    }

    @Test
    public void sortTest() {
        // do a faceted search for counts
        Search srch = db.search("views101/animals");
        SearchResult<Animal> rslt = srch.includeDocs(true)
                .sort("[\"diet<string>\"]")
                .querySearchResult("class:mammal", Animal.class);
        assertNotNull(rslt);
        assertEquals(rslt.getRows().get(0).getOrder()[0].toString(), ("carnivore"));
        assertEquals(rslt.getRows().get(1).getOrder()[0].toString(), ("herbivore"));
        assertEquals(rslt.getRows().get(5).getOrder()[0].toString(), ("omnivore"));
    }

    @Test
    public void groupSortTest() {
        Search srch = db.search("views101/animals");
        Map<String, List<Animal>> groups = srch.includeDocs(true)
                .groupField("diet", false)
                .groupSort("[\"-diet<string>\"]")
                .queryGroups("l*", Animal.class);
        assertNotNull(groups);
        assertEquals(groups.size(), 2);
        Iterator<String> it = groups.keySet().iterator();
        assertEquals("omnivore", it.next()); // diet in reverse order
        assertEquals("herbivore", it.next());
    }


    @Test
    public void drillDownTest() {
        // do a faceted search for drilldown
        Search srch = db.search("views101/animals");
        SearchResult<Animal> rslt = srch.includeDocs(true)
                .counts(new String[]{"class", "diet"})
                .ranges("{ \"min_length\": {\"small\": \"[0 TO 1.0]\","
                        + "\"medium\": \"[1.1 TO 3.0]\", \"large\": \"[3.1 TO 9999999]\"} }")
                .drillDown("class", "mammals")
                .querySearchResult("class:mammal", Animal.class);
        assertNotNull(rslt);
        assertNotNull(rslt.getRanges());
        assertEquals(rslt.getRanges().entrySet().size(), 1);
        assertEquals(rslt.getRanges().get("min_length").entrySet().size(), 3);
        assertEquals(rslt.getRanges().get("min_length").get("small"), new Long(0));
        assertEquals(rslt.getRanges().get("min_length").get("medium"), new Long(0));
        assertEquals(rslt.getRanges().get("min_length").get("large"), new Long(0));
    }


    @Test
    public void bookmarkTest() {
        Search srch = db.search("views101/animals");
        SearchResult<Animal> rslt = srch.limit(4)
                .querySearchResult("class:mammal", Animal.class);

        Search srch1 = db.search("views101/animals");
        srch1.bookmark(rslt.getBookmark())
                .querySearchResult("class:mammal", Animal.class);

    }

    private void escapingTest(String expectedResult, String query) {
        URIBuilder uriBuilder = new URIBuilder();
        uriBuilder.scheme("https");
        uriBuilder.host(CloudantClientHelper.COUCH_USERNAME + ".cloudant.com");
        uriBuilder.port(443);
        uriBuilder.path("/animaldb/_design/views101/_search/animals");
        uriBuilder.query("include_docs", true);
        uriBuilder.query("q", query);
        URI uri = uriBuilder.build();

        String uriBaseString = account.getBaseUri().toASCIIString();

        String expectedUriString = uriBaseString + "animaldb/_design/views101/_search/animals?include_docs=true&q=" + expectedResult;

        String uriString = uri.toASCIIString();
        assertEquals(expectedUriString, uriString);
    }

    @Test
    public void escapedPlusTest() {
        escapingTest("class:mammal%2Btest%2Bescaping", "class:mammal+test+escaping");
    }

    @Test
    public void escapedEqualsTest() {
        escapingTest("class:mammal%3Dtest%3Descaping", "class:mammal=test=escaping");
    }

    @Test
    public void escapedAmpersandTest() {
        escapingTest("class:mammal%26test%26escaping", "class:mammal&test&escaping");
    }

}

