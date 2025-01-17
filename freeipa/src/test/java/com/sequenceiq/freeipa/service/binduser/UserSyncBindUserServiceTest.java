package com.sequenceiq.freeipa.service.binduser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sequenceiq.cloudbreak.common.exception.NotFoundException;
import com.sequenceiq.freeipa.api.v1.ldap.model.describe.DescribeLdapConfigResponse;
import com.sequenceiq.freeipa.client.FreeIpaClient;
import com.sequenceiq.freeipa.client.FreeIpaClientException;
import com.sequenceiq.freeipa.client.model.Role;
import com.sequenceiq.freeipa.client.model.User;
import com.sequenceiq.freeipa.entity.Stack;
import com.sequenceiq.freeipa.ldap.LdapConfig;
import com.sequenceiq.freeipa.ldap.LdapConfigConverter;
import com.sequenceiq.freeipa.ldap.LdapConfigService;
import com.sequenceiq.freeipa.ldap.v1.LdapConfigV1Service;
import com.sequenceiq.freeipa.service.freeipa.FreeIpaClientFactory;

@ExtendWith(MockitoExtension.class)
class UserSyncBindUserServiceTest {

    private static final String ACCOUNT_ID = "accountId";

    private static final String ENV_CRN = "crn:cdp:environments:us-west-1:accountId:environment:4c5ba74b-c35e-45e9-9f47-123456789876";

    private static final String USERSYNC_USER_POSTFIX = "usersync";

    private static final String BIND_USER_POSTFIX = USERSYNC_USER_POSTFIX + "-123456789876";

    private static final String USER_ADMINISTRATOR_ROLE = "User Administrator";

    @Mock
    private LdapConfigV1Service ldapConfigV1Service;

    @Mock
    private LdapConfigService ldapConfigService;

    @Mock
    private LdapBindUserNameProvider userNameProvider;

    @Mock
    private FreeIpaClientFactory ipaClientFactory;

    @Mock
    private LdapConfigConverter ldapConfigConverter;

    @InjectMocks
    private UserSyncBindUserService underTest;

    @Test
    public void testGetUserSyncLdapConfig() {
        LdapConfig ldapConfig = new LdapConfig();
        when(ldapConfigService.find(ENV_CRN, ACCOUNT_ID, BIND_USER_POSTFIX)).thenReturn(Optional.of(ldapConfig));
        DescribeLdapConfigResponse response = new DescribeLdapConfigResponse();
        when(ldapConfigConverter.convertLdapConfigToDescribeLdapConfigResponse(ldapConfig)).thenReturn(response);

        DescribeLdapConfigResponse result = underTest.getUserSyncLdapConfigIfExistsOrThrowNotFound(ENV_CRN, ACCOUNT_ID);

        assertEquals(response, result);
    }

    @Test
    public void testGetUserSyncLdapConfigMissing() {
        when(ldapConfigService.find(ENV_CRN, ACCOUNT_ID, BIND_USER_POSTFIX)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> underTest.getUserSyncLdapConfigIfExistsOrThrowNotFound(ENV_CRN, ACCOUNT_ID));
    }

    @Test
    public void testCreateUserAndLdapConfig() throws FreeIpaClientException {
        Stack stack = new Stack();
        stack.setEnvironmentCrn(ENV_CRN);
        stack.setAccountId(ACCOUNT_ID);
        String bindusername = "ldapbind-" + BIND_USER_POSTFIX;
        when(userNameProvider.createBindUserName(BIND_USER_POSTFIX)).thenReturn(bindusername);
        FreeIpaClient ipaClient = mock(FreeIpaClient.class);
        when(ipaClientFactory.getFreeIpaClientForStack(stack)).thenReturn(ipaClient);

        underTest.createUserAndLdapConfig(stack);

        verify(ldapConfigService).delete(ENV_CRN, ACCOUNT_ID, BIND_USER_POSTFIX);
        verify(ldapConfigV1Service).createNewLdapConfig(ENV_CRN, BIND_USER_POSTFIX, stack, true);
        verify(ipaClient).addRoleMember(USER_ADMINISTRATOR_ROLE, Set.of(bindusername), Set.of(), Set.of(), Set.of(), Set.of());
    }

    @Test
    public void testBindUserAndConfigAlreadyExist() throws FreeIpaClientException {
        Stack stack = new Stack();
        stack.setEnvironmentCrn(ENV_CRN);
        stack.setAccountId(ACCOUNT_ID);
        when(ldapConfigService.find(ENV_CRN, ACCOUNT_ID, BIND_USER_POSTFIX)).thenReturn(Optional.of(new LdapConfig()));
        FreeIpaClient ipaClient = mock(FreeIpaClient.class);
        when(ipaClientFactory.getFreeIpaClientForStack(stack)).thenReturn(ipaClient);
        String bindusername = "ldapbind-" + BIND_USER_POSTFIX;
        when(userNameProvider.createBindUserName(BIND_USER_POSTFIX)).thenReturn(bindusername);
        when(ipaClient.userFind(bindusername)).thenReturn(Optional.of(new User()));
        Role role = new Role();
        role.setMemberUser(List.of(bindusername));
        when(ipaClient.showRole(USER_ADMINISTRATOR_ROLE)).thenReturn(role);

        boolean result = underTest.doesBindUserAndConfigAlreadyExist(stack);

        assertTrue(result);
    }

    @Test
    public void testBindUserAndConfigAlreadyExistRoleMissingUser() throws FreeIpaClientException {
        Stack stack = new Stack();
        stack.setEnvironmentCrn(ENV_CRN);
        stack.setAccountId(ACCOUNT_ID);
        when(ldapConfigService.find(ENV_CRN, ACCOUNT_ID, BIND_USER_POSTFIX)).thenReturn(Optional.of(new LdapConfig()));
        FreeIpaClient ipaClient = mock(FreeIpaClient.class);
        when(ipaClientFactory.getFreeIpaClientForStack(stack)).thenReturn(ipaClient);
        String bindusername = "ldapbind-" + BIND_USER_POSTFIX;
        when(userNameProvider.createBindUserName(BIND_USER_POSTFIX)).thenReturn(bindusername);
        when(ipaClient.userFind(bindusername)).thenReturn(Optional.of(new User()));
        Role role = new Role();
        when(ipaClient.showRole(USER_ADMINISTRATOR_ROLE)).thenReturn(role);

        boolean result = underTest.doesBindUserAndConfigAlreadyExist(stack);

        assertFalse(result);
    }

    @Test
    public void testBindUserAndConfigAlreadyExistMissingUser() throws FreeIpaClientException {
        Stack stack = new Stack();
        stack.setEnvironmentCrn(ENV_CRN);
        stack.setAccountId(ACCOUNT_ID);
        when(ldapConfigService.find(ENV_CRN, ACCOUNT_ID, BIND_USER_POSTFIX)).thenReturn(Optional.of(new LdapConfig()));
        FreeIpaClient ipaClient = mock(FreeIpaClient.class);
        when(ipaClientFactory.getFreeIpaClientForStack(stack)).thenReturn(ipaClient);
        String bindusername = "ldapbind-" + BIND_USER_POSTFIX;
        when(userNameProvider.createBindUserName(BIND_USER_POSTFIX)).thenReturn(bindusername);
        when(ipaClient.userFind(bindusername)).thenReturn(Optional.empty());
        Role role = new Role();
        role.setMemberUser(List.of(bindusername));
        when(ipaClient.showRole(USER_ADMINISTRATOR_ROLE)).thenReturn(role);

        boolean result = underTest.doesBindUserAndConfigAlreadyExist(stack);

        assertFalse(result);
    }

    @Test
    public void testBindUserAndConfigAlreadyExistMissingLdapConfig() throws FreeIpaClientException {
        Stack stack = new Stack();
        stack.setEnvironmentCrn(ENV_CRN);
        stack.setAccountId(ACCOUNT_ID);
        when(ldapConfigService.find(ENV_CRN, ACCOUNT_ID, BIND_USER_POSTFIX)).thenReturn(Optional.empty());

        boolean result = underTest.doesBindUserAndConfigAlreadyExist(stack);

        assertFalse(result);
    }
}