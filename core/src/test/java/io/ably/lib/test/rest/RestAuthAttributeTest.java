package io.ably.lib.test.rest;

import io.ably.lib.rest.AblyBase;
import io.ably.lib.rest.Auth;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Capability;
import io.ably.lib.types.ClientOptions;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by VOstopolets on 9/3/16.
 */
public abstract class RestAuthAttributeTest extends ParameterizedTest {

    private AblyBase ably;

    @Before
    public void setupClient() throws Exception {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        opts.useTokenAuth = true;
        ably = createAblyRest(opts);
    }

    /**
     * Stores the AuthOptions and TokenParams arguments as defaults for subsequent authorizations
     * <p>
     * Spec: RSA10g,RSA10j
     * </p>
     */
    @Ignore("FIXME: flaky test")
    @Test
    public void auth_stores_options_params() {
        try {
            /* init custom TokenParams */
            Capability capability = new Capability();
            capability.addResource("testchannel", "subscribe");
            final String capabilityStr = capability.toString();
            final String testClientId = "firstClientId";
            Auth.TokenParams tokenParams = new Auth.TokenParams() {{
                ttl = 4000L;
                clientId = testClientId;
                capability = capabilityStr;
            }};

            /* init custom AuthOptions */
            Auth.AuthOptions authOptions = new Auth.AuthOptions() {{
                authCallback = new Auth.TokenCallback() {
                    private AblyBase ably = createAblyRest(createOptions(testVars.keys[0].keyStr));

                    @Override
                    public Object getTokenRequest(Auth.TokenParams params) throws AblyException {
                        return ably.auth.requestToken(params, null);
                    }
                };
                key = testVars.keys[1].keyStr;
            }};

            /* authorise with custom options
             * Deliberate use of British spelling alias authorise() to check that
             * it works (0.9 RSA10l) */
            @SuppressWarnings("deprecation")
            Auth.TokenDetails tokenDetails1 = ably.auth.authorise(tokenParams, authOptions);

            /* Verify that,
             * tokenDetails1 isn't null,
             * capability and clientId equals to the values of corresponding attributes in tokenParams */
            assertNotNull(tokenDetails1);
            assertEquals(tokenDetails1.clientId, testClientId);
            assertEquals(tokenDetails1.capability, capabilityStr);

            /* wait until token expires */
            try {
                Thread.sleep(5000L);
            } catch(InterruptedException ie) {}

            /* authorize with default options */
            Auth.TokenDetails tokenDetails2 = ably.auth.authorize(null, null);

            /* Verify that,
             * tokenDetails2 isn't null,
             * new token has to be issued,
             * capability and clientId for different TokenDetails are the same */
            assertNotNull(tokenDetails2);
            assertNotEquals(tokenDetails1.token, tokenDetails2.token);
            assertEquals(tokenDetails1.capability, tokenDetails2.capability);
            assertEquals(tokenDetails1.clientId, tokenDetails2.clientId);
        } catch (Exception e) {
            e.printStackTrace();
            fail("auth_stores_options_params: Unexpected exception");
        }
    }

    /**
     * Verify that {@link AuthOptions#queryTime} attribute don't stored/used for subsequent authorizations
     * <p>
     * Spec: RSA10g
     * </p>
     */
    @Test
    public void auth_stores_options_exception_querytime() {
        try {
            final long fakeServerTime = -1000;
            final String expectedClientId = "testClientId";
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.clientId = expectedClientId;
            AblyBase ablyForTime = createAblyRest(opts, fakeServerTime) ;
            final Auth.AuthOptions authOptions = new Auth.AuthOptions();
            authOptions.key = ablyForTime.options.key;
            authOptions.queryTime = true;
            Auth.TokenParams tokenParams = new Auth.TokenParams();

            /* create token request with custom AuthOptions that has attribute queryTime */
            Auth.TokenRequest tokenRequest = ablyForTime.auth.createTokenRequest(tokenParams, authOptions);

            /* verify that issued time of server equals fake expected value */
            assertEquals(expectedClientId, tokenRequest.clientId);
            assertEquals(fakeServerTime, tokenRequest.timestamp);

            /* authorize for store custom AuthOptions that has attribute queryTime */
            try {
                ablyForTime.auth.authorize(tokenParams, authOptions);
            } catch (Throwable e) {
            }

            /* create token request with stored AuthOptions */
            tokenRequest = ablyForTime.auth.createTokenRequest(tokenParams, null);

            /* Verify that,
            *    - timestamp not equals fake server time
            *    - timestamp equals local time */
            assertEquals(expectedClientId, tokenRequest.clientId);
            assertNotEquals(fakeServerTime, tokenRequest.timestamp);
            long localTime = System.currentTimeMillis();
            assertTrue((tokenRequest.timestamp >= (localTime - 500)) && (tokenRequest.timestamp <= (localTime + 500)));
        } catch (Exception e) {
            e.printStackTrace();
            fail("auth_stores_options_exception_querytime: Unexpected exception");
        }
    }

    /**
     * Verify that {@link TokenParams#timestamp} attribute don't stored/used for subsequent authorizations
     * <p>
     * Spec: RSA10g
     * </p>
     */
    @Test
    public void auth_stores_options_exception_timestamp() {
        final String expectedClientId = "clientIdForToken";
        final long expectedTimestamp = 11111;
        try {
            /* init ably for token */
            final ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            optsForToken.clientId = expectedClientId;
            final AblyBase ablyForToken = createAblyRest(optsForToken);

            /* create custom token callback for capturing timestamp values */
            final List<Long> timestampCapturedList = new ArrayList<>();
            Auth.TokenCallback tokenCallback = new Auth.TokenCallback() {
                private List<Long> timestampCapturedList;

                public Auth.TokenCallback setTimestampCapturedList(List<Long> timestampCapturedList) {
                    this.timestampCapturedList = timestampCapturedList;
                    return this;
                }

                @Override
                public Object getTokenRequest(Auth.TokenParams params) throws AblyException {
                    this.timestampCapturedList.add(params.timestamp);
                    return ablyForToken.auth.requestToken(null, null);
                }
            }.setTimestampCapturedList(timestampCapturedList);

            /* authorize with custom timestamp */
            Auth.AuthOptions authOptions = new Auth.AuthOptions();
            authOptions.key = ably.options.key;
            authOptions.authCallback = tokenCallback;
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            tokenParams.timestamp = expectedTimestamp;
            Auth.TokenDetails tokenDetails1 = ably.auth.authorize(tokenParams, authOptions);
            final String token1 = tokenDetails1.token;
            final String clientId1 = tokenDetails1.clientId;

            /* force authorize with stored TokenParams values */
            Auth.TokenDetails tokenDetails2 = ably.auth.authorize(null, authOptions);
            final String token2 = tokenDetails2.token;
            final String clientId2 = tokenDetails2.clientId;

            /* Verify that,
            *    - new token was issued
            *    - authorize called twice
            *    - first timestamp value equals expected timestamp
            *    - second timestamp value is not expected
            * tokenDetails1 and tokenDetails2 aren't null,
            * the values of each attribute are equals */
            assertNotNull(tokenDetails1);
            assertNotNull(tokenDetails2);
            assertEquals(expectedClientId, clientId1);
            assertEquals(clientId1, clientId2);
            assertNotEquals(token1, token2);
            assertThat(timestampCapturedList.size(), is(equalTo(2)));
            assertEquals((long) timestampCapturedList.get(0), expectedTimestamp);
            assertNotEquals(timestampCapturedList.get(0), timestampCapturedList.get(1));
        } catch (Exception e) {
            e.printStackTrace();
            fail("auth_stores_options_exception_timestamp: Unexpected exception");
        }
    }

    /**
     * Verify
     * will to issue a new token even if an existing token exists.
     * <p>
     * Spec: RSA10d
     * </p>
     */
    @Test
    public void auth_authorize_force() {
        try {
            /* authorize with default options */
            Auth.TokenDetails tokenDetails1 = ably.auth.authorize(null, null);

            /* init custom AuthOptions */
            final String custom_test_value = "test_forced_token";
            Auth.AuthOptions authOptions = new Auth.AuthOptions() {{
                authCallback = new Auth.TokenCallback() {
                    @Override
                    public Object getTokenRequest(Auth.TokenParams params) throws AblyException {
                        return custom_test_value;
                    }
                };
            }};

            /* authorize with custom AuthOptions */
            Auth.TokenDetails tokenDetails2 = ably.auth.authorize(null, authOptions);

            /* Verify that,
             * tokenDetails1 and tokenDetails2 aren't null,
             * tokens are different,
             * token from tokenDetails2 equals custom_test_value */
            assertNotNull(tokenDetails1);
            assertNotNull(tokenDetails2);
            assertNotEquals(tokenDetails1.token, tokenDetails2.token);
            assertEquals(tokenDetails2.token, custom_test_value);
        } catch (Exception e) {
            e.printStackTrace();
            fail("auth_custom_options_authorize: Unexpected exception");
        }
    }
}
