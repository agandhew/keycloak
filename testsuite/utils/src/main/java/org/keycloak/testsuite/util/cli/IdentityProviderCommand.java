package org.keycloak.testsuite.util.cli;

import org.keycloak.models.AuthenticationFlowModel;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IdentityProviderCommand {

    public static class Create extends AbstractCommand {

        private String idpPrefix;
        private String realmName;

        @Override
        public String getName() {
            return "createIdentityProviders";
        }

        @Override
        protected void doRunCommand(KeycloakSession session) {
            idpPrefix = getArg(0);
            realmName = getArg(1);
            int first = getIntArg(2);
            int count = getIntArg(3);
            int batchCount = getIntArg(4);

            BatchTaskRunner.runInBatches(first, count, batchCount, session.getKeycloakSessionFactory(), this::createIdentityProvidersInBatch);

            log.infof("Command finished. All idps from %s to %s created", idpPrefix + first, idpPrefix
                    + (first + count - 1));
        }

        private void createIdentityProvidersInBatch(KeycloakSession session, int first, int count) {
            RealmModel realm = session.realms().getRealmByName(realmName);
            if (realm == null) {
                log.errorf("Unknown realm: %s", realmName);
                throw new HandledException();
            }

            int last = first + count;
            for (int counter = first; counter < last; counter++) {
                String idpName = idpPrefix + counter;
                AuthenticationFlowModel newFlow = new AuthenticationFlowModel();
                newFlow.setAlias("AutoLink");
                newFlow.setDescription("AutoLink");
                newFlow.setProviderId("basic-flow");
                newFlow.setBuiltIn(false);
                newFlow.setTopLevel(true);
                newFlow = realm.addAuthenticationFlow(newFlow);
                IdentityProviderModel idpModel = createRandomIdpModel(idpName, newFlow);
                realm.addIdentityProvider(idpModel);
            }
            log.infof("idps from %s to %s created", idpPrefix + first, idpPrefix + (last - 1));
        }

        private IdentityProviderModel createRandomIdpModel(String idpName , AuthenticationFlowModel flow){
            IdentityProviderModel identityProviderModel = new IdentityProviderModel();
            identityProviderModel.setProviderId(UUID.randomUUID().toString());
            identityProviderModel.setAlias(idpName);
            identityProviderModel.setDisplayName(idpName);

            //identityProviderModel.setInternalId(entity.getInternalId());
            Map<String, String> config = new HashMap<>();
            Map<String, String> copy = new HashMap<>();
            copy.putAll(config);
            identityProviderModel.setConfig(copy);
            identityProviderModel.setEnabled(true);
            identityProviderModel.setLinkOnly(true);
            identityProviderModel.setTrustEmail(true);
            identityProviderModel.setAuthenticateByDefault(false);
            identityProviderModel.setFirstBrokerLoginFlowId(flow.getId());
            identityProviderModel.setPostBrokerLoginFlowId(flow.getId());
            identityProviderModel.setStoreToken(true);
            identityProviderModel.setAddReadTokenRoleOnCreate(true);
            return identityProviderModel;
        }

        @Override
        public String printUsage() {
            return super.printUsage() + " <idp-prefix> <realm-name> <starting-idp-offset> <total-count> <batch-size>. " +
                    "\n'total-count' refers to total count of newly created idps. 'batch-size' refers to number of created idps in each transaction. 'starting-idp-offset' refers to starting idp offset." +
                    "\nFor example if 'starting-idp-offset' is 15 and total-count is 10 and idp-prefix is 'test', it will create idps test15, test16, test17, ... , test24" +
                    "Example usage: " + super.printUsage() + " test demo 0 500 100";
        }

    }
}
