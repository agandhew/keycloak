package org.keycloak.testsuite.admin.group;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.keycloak.testsuite.arquillian.annotation.AuthServerContainerExclude.AuthServer.REMOTE;

import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ArrayUtils;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.models.GroupProvider;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testsuite.arquillian.AuthServerTestEnricher;
import org.keycloak.testsuite.arquillian.annotation.AuthServerContainerExclude;
import org.keycloak.testsuite.arquillian.containers.KeycloakQuarkusServerDeployableContainer;
import org.wildfly.extras.creaper.core.online.OnlineManagementClient;
import org.wildfly.extras.creaper.core.online.operations.admin.Administration;

@AuthServerContainerExclude(REMOTE)
public class GroupSearchTest extends AbstractGroupTest {
  @ArquillianResource
  protected ContainerController controller;

  private static final String GROUP1 = "group1";
  private static final String GROUP2 = "group2";
  private static final String GROUP3 = "group3";

  private String group1Id;
  private String group2Id;
  private String group3Id;

  private static final String ATTR_ORG_NAME = "org";
  private static final String ATTR_ORG_VAL = "Test_\"organisation\"";
  private static final String ATTR_URL_NAME = "url";
  private static final String ATTR_URL_VAL = "https://foo.bar/clflds";
  private static final String ATTR_FILTERED_NAME = "filtered";
  private static final String ATTR_FILTERED_VAL = "does_not_matter";

  private static final String ATTR_QUOTES_NAME = "test \"123\"";

  private static final String ATTR_QUOTES_NAME_ESCAPED = "\"test \\\"123\\\"\"";
  private static final String ATTR_QUOTES_VAL = "field=\"blah blah\"";
  private static final String ATTR_QUOTES_VAL_ESCAPED = "\"field=\\\"blah blah\\\"\"";

  private static final String SEARCHABLE_ATTRS_PROP = "keycloak.group.searchableAttributes";

  @Before
  public void init() {

    GroupRepresentation group1 = new GroupRepresentation();
    GroupRepresentation group2 = new GroupRepresentation();
    GroupRepresentation group3 = new GroupRepresentation();

    group1.setAttributes(new HashMap<String, List<String>>() {{
      put(ATTR_ORG_NAME, Collections.singletonList(ATTR_ORG_VAL));
      put(ATTR_URL_NAME, Collections.singletonList(ATTR_URL_VAL));
    }});

    group2.setAttributes(new HashMap<String, List<String>>() {{
      put(ATTR_FILTERED_NAME, Collections.singletonList(ATTR_FILTERED_VAL));
      put(ATTR_URL_NAME, Collections.singletonList(ATTR_URL_VAL));
    }});

    group3.setAttributes(new HashMap<String, List<String>>() {{
      put(ATTR_ORG_NAME, Collections.singletonList("fake group"));
      put(ATTR_QUOTES_NAME, Collections.singletonList(ATTR_QUOTES_VAL));
    }});

    group1.setName("group1");
    group2.setName("group2");
    group3.setName("group3");

    group1 = createGroup(testRealmResource(), group1);
    group2 = createGroup(testRealmResource(), group2);
    group3 = createGroup(testRealmResource(), group3);

    group1Id = group1.getId();
    group2Id = group2.getId();
    group3Id = group3.getId();
  }

  @After
  public void teardown() {
    testContext.setTestRealmReps(new ArrayList<>());
    RealmResource realmResource = adminClient.realm(testRealmId);
    realmResource.remove();
  }

  public RealmResource testRealmResource() {
    return adminClient.realm(testRealmId);
  }

  @Test
  public void testQuerySearch() throws Exception {
    try {
      // I assume the structure of this query is going to be not quite right but something before this
      // point is broken at the moment so I can't debug and figure it out
      configureSearchableAttributes(ATTR_URL_NAME, ATTR_ORG_NAME, ATTR_QUOTES_NAME);
      search(String.format("%s:%s", ATTR_ORG_NAME, ATTR_ORG_VAL), GROUP1);
      search(String.format("%s:%s", ATTR_URL_NAME, ATTR_URL_VAL), GROUP1, GROUP2);
      search(String.format("%s:%s %s:%s", ATTR_ORG_NAME, ATTR_ORG_VAL, ATTR_URL_NAME, ATTR_URL_VAL),
          GROUP1);
      search(String.format("%s:%s %s:%s", ATTR_ORG_NAME, "wrong val", ATTR_URL_NAME, ATTR_URL_VAL));
      search(String.format("%s:%s", ATTR_QUOTES_NAME_ESCAPED, ATTR_QUOTES_VAL_ESCAPED), GROUP3);

      // "filtered" attribute won't take effect when JPA is used
      String[] expectedRes = isJpaStore() ? new String[]{GROUP1, GROUP2} : new String[]{GROUP2};
      search(String.format("%s:%s %s:%s", ATTR_URL_NAME, ATTR_URL_VAL, ATTR_FILTERED_NAME, ATTR_FILTERED_VAL), expectedRes);
    }
    finally {
      resetSearchableAttributes();
    }
  }

  @Test
  public void testJpaSearchableAttributesUnset() {
    String[] expectedRes = {GROUP1};
    // JPA store removes all attributes by default, i.e. returns all clients
    if (isJpaStore()) {
      expectedRes = ArrayUtils.addAll(expectedRes, GROUP2, GROUP3);
    }

    search(String.format("%s:%s", ATTR_ORG_NAME, ATTR_ORG_VAL), expectedRes);
  }

  private void search(String searchQuery, String... expectedGroupIds) {
    GroupsResource search = testRealmResource().groups();
    List<String> found = search.query(searchQuery).stream()
        .map(GroupRepresentation::getName)
        .collect(Collectors.toList());
    assertThat(found, containsInAnyOrder(expectedGroupIds));
  }

  void configureSearchableAttributes(String... searchableAttributes) throws Exception {
    log.infov("Configuring searchableAttributes");

    if (suiteContext.getAuthServerInfo().isUndertow()) {
      controller.stop(suiteContext.getAuthServerInfo().getQualifier());
      System.setProperty(SEARCHABLE_ATTRS_PROP, String.join(",", searchableAttributes));
      controller.start(suiteContext.getAuthServerInfo().getQualifier());
    } else if (suiteContext.getAuthServerInfo().isJBossBased()) {
      searchableAttributes = Arrays.stream(searchableAttributes).map(a -> a.replace("\"", "\\\\\\\"")).toArray(String[]::new);
      String s = "\\\"" + String.join("\\\",\\\"", searchableAttributes) + "\\\"";
      executeCli("/subsystem=keycloak-server/spi=group:add()",
          "/subsystem=keycloak-server/spi=group/provider=jpa/:add(properties={searchableAttributes => \"[" + s + "]\"},enabled=true)");
    } else if(suiteContext.getAuthServerInfo().isQuarkus()) {
      searchableAttributes = Arrays.stream(searchableAttributes)
          .map(a -> a.replace(" ", "\\ ").replace("\"", "\\\\\\\""))
          .toArray(String[]::new);
      String s = String.join(",",searchableAttributes);
      controller.stop(suiteContext.getAuthServerInfo().getQualifier());
      KeycloakQuarkusServerDeployableContainer container = (KeycloakQuarkusServerDeployableContainer)suiteContext.getAuthServerInfo().getArquillianContainer().getDeployableContainer();
      container.setAdditionalBuildArgs(
          Collections.singletonList("--spi-group-jpa-searchable-attributes=\""+ s + "\""));
      controller.start(suiteContext.getAuthServerInfo().getQualifier());
    } else {
      throw new RuntimeException("Don't know how to config");
    }

    reconnectAdminClient();
  }

  void resetSearchableAttributes() throws Exception {
    log.info("Reset searchableAttributes");

    if (suiteContext.getAuthServerInfo().isUndertow()) {
      controller.stop(suiteContext.getAuthServerInfo().getQualifier());
      System.clearProperty(SEARCHABLE_ATTRS_PROP);
      controller.start(suiteContext.getAuthServerInfo().getQualifier());
    } else if (suiteContext.getAuthServerInfo().isJBossBased()) {
      executeCli("/subsystem=keycloak-server/spi=client:remove");
    } else if(suiteContext.getAuthServerInfo().isQuarkus()) {
      KeycloakQuarkusServerDeployableContainer container = (KeycloakQuarkusServerDeployableContainer)suiteContext.getAuthServerInfo().getArquillianContainer().getDeployableContainer();
      container.setAdditionalBuildArgs(Collections.emptyList());
      container.restartServer();
    } else {
      throw new RuntimeException("Don't know how to config");
    }

    reconnectAdminClient();
  }

  private void executeCli(String... commands) throws Exception {
    OnlineManagementClient client = AuthServerTestEnricher.getManagementClient();
    Administration administration = new Administration(client);

    log.debug("Running CLI commands:");
    for (String c : commands) {
      log.debug(c);
      client.execute(c).assertSuccess();
    }
    log.debug("Done");

    administration.reload();

    client.close();
  }

  private boolean isJpaStore() {
    String providerId = testingClient.server()
        .fetchString(s -> s.getKeycloakSessionFactory().getProviderFactory(GroupProvider.class).getId());
    log.info("Detected store: " + providerId);
    return "\"jpa\"".equals(providerId); // there are quotes for some reason
  }

  @Override
  public void addTestRealms(List<RealmRepresentation> testRealms) {
    RealmRepresentation testRealmRep = loadTestRealm(testRealms);

    testRealmRep.setEventsEnabled(true);

    List<UserRepresentation> users = testRealmRep.getUsers();

    UserRepresentation user = new UserRepresentation();
    user.setUsername("direct-login");
    user.setEmail("direct-login@localhost");
    user.setEnabled(true);
    List<CredentialRepresentation> credentials = new LinkedList<>();
    CredentialRepresentation credential = new CredentialRepresentation();
    credential.setType(CredentialRepresentation.PASSWORD);
    credential.setValue("password");
    credentials.add(credential);
    user.setCredentials(credentials);
    users.add(user);

    List<ClientRepresentation> clients = testRealmRep.getClients();

    ClientRepresentation client = new ClientRepresentation();
    client.setClientId("resource-owner");
    client.setDirectAccessGrantsEnabled(true);
    client.setSecret("secret");
    clients.add(client);
  }
}
