package net.clementlevallois.nocodeapp.web.front.stripe;

import jakarta.annotation.PostConstruct;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import jakarta.servlet.annotation.MultipartConfig;
import java.io.IOException;
import java.io.Serializable;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient.MicroserviceCallException;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.utils.UrlParamCleaner;

/**
 *
 * @author clevallois
 */
@Named
@ViewScoped
@MultipartConfig
public class StripeBean implements Serializable {

    private String urlCheckoutMonthlyProPlan;
    private String urlCheckoutMonthlyPatronPlan;
    private String urlParamStripeSessionIdReturnedByCheckout;
    private String urlParamEmail;
    private String emailInputField;
    private Integer remainingCredits;
    private boolean creditsCheckPerformed = false;
    private boolean eligibleForRefund = true;
    private boolean emailButtonIsDisabled = false;
    private static final String PRICE_PRO_MONTHLY_TEST = "price_0Qwgx037XvgprGmlwZsa8pfS";
    private static final String PRICE_PATRON_MONTHLY_TEST = "price_0QzbmB37XvgprGmlzLwTUges";
    private static final String PRICE_PRO_MONTHLY = "price_0R1CiN37XvgprGml2moLXgHF";
    private static final String PRICE_PATRON_MONTHLY = "price_0R1CiH37XvgprGmlxOpPcsWo";
    private static final String MONTHLY_CREDITS_PRO = "100";
    private static final String MONTHLY_CREDITS_PATRON = "100";
    private String MAX_USED_CREDITS_FOR_REFUND_ELIGIBILITY = "5";

    private int portPayment;

    private static final boolean TEST = RemoteLocal.isLocal();
    private static final String BILLING_PORTAL_URL_TEST = "https://billing.stripe.com/p/login/test_14k5og3L67BEf28eUU";
    private static final String BILLING_PORTAL_URL_PROD = "https://billing.stripe.com/p/login/dR6171e74flG8nK5kk";

    private static final Set<String> allPaidPlansForThisServiceTest = Set.of(PRICE_PRO_MONTHLY_TEST);
    private static final Set<String> allPaidPlansForThisService = Set.of(PRICE_PRO_MONTHLY);

    private Properties privateProperties;

    private static final Logger LOG = Logger.getLogger(StripeBean.class.getName());

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    MicroserviceHttpClient httpClient;

    @Inject
    SessionBean sessionBean;

    @PostConstruct
    public void init() {
        privateProperties = applicationProperties.getPrivateProperties();
        portPayment = Integer.parseInt(privateProperties.getProperty("port-payment-ops"));
        creditsCheckPerformed = false;
    }

    public void initializeRedirectState() {
        this.emailButtonIsDisabled = false;
    }

    public void decisionTreeBeforeRenderingIndexPage() {
        if (isRedirectFromStripeCheckout()) {
            Customer customer = getCustomerInfoBySessionId(urlParamStripeSessionIdReturnedByCheckout);
            if (customer != null) {
                sessionBean.setHash(customer.getHash());
                sendWelcomeEmailAfterCheckout(customer.getEmail(), customer.getName(), customer.getHash());
            }
        }
    }

    public void populatePlanUrls() {
        boolean emailParamAbsent = urlParamEmail == null || urlParamEmail.isBlank();
        if (emailParamAbsent && !sessionBean.isHashPresent()) {
            try {
                ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
                externalContext.redirect(externalContext.getRequestContextPath() + "/" + "index.html#input-email-container-anchor");
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "Redirect failed", ex);
            }
            return;
        }

        if (emailParamAbsent) {
            urlParamEmail = getEmailFromCustomerHash(sessionBean.getHash());
        }

        if (urlParamEmail != null) {
            if (TEST) {
                urlCheckoutMonthlyProPlan = getCheckoutUrlForServiceAndPrice(PRICE_PRO_MONTHLY_TEST, MONTHLY_CREDITS_PRO, urlParamEmail);
                urlCheckoutMonthlyPatronPlan = getCheckoutUrlForServiceAndPrice(PRICE_PATRON_MONTHLY_TEST, MONTHLY_CREDITS_PATRON, urlParamEmail);
            } else {
                urlCheckoutMonthlyProPlan = getCheckoutUrlForServiceAndPrice(PRICE_PRO_MONTHLY, MONTHLY_CREDITS_PRO, urlParamEmail);
                urlCheckoutMonthlyPatronPlan = getCheckoutUrlForServiceAndPrice(PRICE_PATRON_MONTHLY, MONTHLY_CREDITS_PATRON, urlParamEmail);
            }
        }
    }

    public void redirectToStripeBillingPortal() {
        String hash = sessionBean.getHash();
        String targetUrl = TEST ? BILLING_PORTAL_URL_TEST : BILLING_PORTAL_URL_PROD;

        if (hash != null && !hash.isBlank()) {
            try {
                HttpResponse<String> resp = httpClient
                        .target("http", "localhost", portPayment)
                        .get("/stripe/getStripeBillingUrl")
                        .addQueryParameter("hash", hash)
                        .addQueryParameter("successUrl", RemoteLocal.getDomain() + "/index.html?hash=" + hash)
                        .addQueryParameter("lang", sessionBean.getCurrentLocale().getLanguage())
                        .send(HttpResponse.BodyHandlers.ofString());

                String responseBody = resp.body();
                if (responseBody != null && !responseBody.equals("{}")) {
                    targetUrl = responseBody;
                }
            } catch (MicroserviceCallException e) {
                LOG.log(Level.SEVERE, "API call to get billing URL failed. URI: " + e.getUri() + ", Status: " + e.getStatusCode(), e);
            } catch (IOException | InterruptedException e) {
                LOG.log(Level.SEVERE, "Could not send request to get billing URL.", e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        try {
            FacesContext.getCurrentInstance().getExternalContext().redirect(targetUrl);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Redirect to billing portal failed", ex);
        }
    }

    public Boolean isRedirectFromStripeCheckout() {
        return urlParamStripeSessionIdReturnedByCheckout != null && !urlParamStripeSessionIdReturnedByCheckout.isBlank();
    }

    public void addHashFieldAndCreditsToCustomer(String customerId, String hash, String serviceName, String credits) {
        try {
            httpClient
                    .target("http", "localhost", portPayment)
                    .get("/stripe/addHashFieldAndCreditsToCustomer")
                    .addQueryParameter("hash", hash)
                    .addQueryParameter("credits", credits)
                    .addQueryParameter("customerId", customerId)
                    .addQueryParameter("serviceName", serviceName)
                    .send(HttpResponse.BodyHandlers.ofString());
            LOG.info("Successfully added credits for customer: " + customerId);
        } catch (MicroserviceCallException e) {
            LOG.log(Level.SEVERE, "API call to update Stripe customer failed. URI: " + e.getUri() + ", Status: " + e.getStatusCode(), e);
        } catch (IOException | InterruptedException e) {
            LOG.log(Level.SEVERE, "Could not send request to update Stripe customer.", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public String anyRemainingCreditsBeforeServiceUse() {
        if (creditsCheckPerformed) {
            return "";
        }
        String hash = sessionBean.getHash();
        if (hash == null || hash.isBlank()) {
            return "index.html?faces-redirect=true";
        }
        remainingCredits = getRemainingCredits();
        creditsCheckPerformed = true;
        if (remainingCredits < 1) {
            return "index.html?faces-redirect=true";
        }
        return "";
    }

    public String handleRefund() {
        proceedToRefund(sessionBean.getHash());
        return "/index.html";
    }

    public String proceedToRefund(String hashValueFromUrlParam) {
        String priceIds = TEST ? String.join(";", allPaidPlansForThisServiceTest) : String.join(";", allPaidPlansForThisService);
        try {
            httpClient
                    .target("http", "localhost", portPayment)
                    .get("/stripe/proceedToRefund")
                    .addQueryParameter("hash", hashValueFromUrlParam)
                    .addQueryParameter("priceIds", priceIds)
                    .addQueryParameter("serviceName", SingletonBean.SERVICE_NAME)
                    .send(HttpResponse.BodyHandlers.ofString());
            LOG.info(() -> "Refund processed successfully for hash: " + hashValueFromUrlParam);
        } catch (MicroserviceCallException e) {
            LOG.log(Level.SEVERE, "API call to process refund failed. URI: " + e.getUri() + ", Status: " + e.getStatusCode(), e);
            return null;
        } catch (IOException | InterruptedException e) {
            LOG.log(Level.SEVERE, "Could not send request for refund.", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
        return "";
    }

    public String resetServiceMetadataForThisCustomer(String hashValueFromUrlParam, String serviceName) {
        try {
            httpClient
                    .target("http", "localhost", portPayment)
                    .get("/stripe/resetServiceMetadataForThisCustomer")
                    .addQueryParameter("hash", hashValueFromUrlParam)
                    .addQueryParameter("serviceName", serviceName)
                    .send(HttpResponse.BodyHandlers.ofString());
            LOG.info(() -> "Successfully reset service metadata for hash: " + hashValueFromUrlParam);
        } catch (MicroserviceCallException e) {
            LOG.log(Level.SEVERE, "API call to reset service metadata failed. URI: " + e.getUri() + ", Status: " + e.getStatusCode(), e);
            return null;
        } catch (IOException | InterruptedException e) {
            LOG.log(Level.SEVERE, "Could not send request to reset service metadata.", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
        return "";
    }

    public void manageCredits() {
        String hash = sessionBean.getHash();
        if (hash == null || hash.isBlank()) {
            return;
        }
        try {
            HttpResponse<String> resp = httpClient
                    .target("http", "localhost", portPayment)
                    .get("/stripe/manageCredits")
                    .addQueryParameter("hash", hash)
                    .addQueryParameter("serviceName", SingletonBean.SERVICE_NAME)
                    .addQueryParameter("serviceNameAllCreditsUsed", SingletonBean.SERVICE_NAME_ALL_CREDITS_USED)
                    .send(HttpResponse.BodyHandlers.ofString());
            remainingCredits = Integer.valueOf(resp.body());
        } catch (MicroserviceCallException e) {
            LOG.log(Level.SEVERE, "API call to manage credits failed. URI: " + e.getUri() + ", Status: " + e.getStatusCode(), e);
        } catch (IOException | InterruptedException | NumberFormatException e) {
            LOG.log(Level.SEVERE, "Could not process request to manage credits.", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void updateCustomerWithMetadata(String customerId, Map<String, String> keyValuesToUpdate) {
        JsonObjectBuilder parametersBuilder = Json.createObjectBuilder();
        keyValuesToUpdate.forEach(parametersBuilder::add);
        JsonObject payload = parametersBuilder.build();

        try {
            httpClient
                    .target("http", "localhost", portPayment)
                    .post("/stripe/updateCustomerMetadata")
                    .addQueryParameter("customerId", customerId)
                    .withJsonPayload(payload)
                    .send(HttpResponse.BodyHandlers.ofString());
            LOG.info("Successfully updated metadata for customer: " + customerId);
        } catch (MicroserviceCallException e) {
            LOG.log(Level.SEVERE, "API call to update customer metadata failed. URI: " + e.getUri() + ", Status: " + e.getStatusCode(), e);
        } catch (IOException | InterruptedException e) {
            LOG.log(Level.SEVERE, "Could not send request to update customer metadata.", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public String sendPricingInfoOrLoginUrlViaEmail() {
        if (emailInputField == null || emailInputField.isBlank()) {
            return "";
        }
        emailButtonIsDisabled = true;
        String hash = getHashFromCustomerEmail(emailInputField);
        sessionBean.setHash(hash);

        if (hash != null && !hash.isBlank()) {
            remainingCredits = getRemainingCredits();
            creditsCheckPerformed = true;
            if (remainingCredits < 1) {
                return "plans/pricing_table.html?hash=" + hash + "&email=" + emailInputField + "faces-redirect=true";
            }
        }
        sendEmail(hash);
        return "";
    }

    private void sendEmail(String hash) {
        String subject;
        String bodyEmail;

        if (hash != null && !hash.isBlank()) {
            subject = "👋 Your login link to Nocodefunctions";
            bodyEmail = "Hi!\n\nThis is your login link to Nocodefunctions:\n" + RemoteLocal.getDomain() + "/?hash=" + hash + "\n\nEnjoy nocodefunctions!";
        } else {
            String urlPricingPlans = RemoteLocal.getDomain() + "/plans/pricing_table.html?email=" + emailInputField;
            subject = "👋 **ACTION REQUIRED** Sign up to Nocodefunctions";
            bodyEmail = "Hi!\n\nTo continue signing up to Nocodefunctions, go here:\n\n" + urlPricingPlans;
        }

        JsonObject payload = Json.createObjectBuilder()
                .add("emailTo", emailInputField)
                .add("emailFrom", "for-help-do-not-email-but-go-to-nocodefunctions-dot-com-click-billing@nocodefunctions.com")
                .add("subject", subject)
                .add("bodyEmail", bodyEmail)
                .add("serviceName", SingletonBean.SERVICE_NAME)
                .build();
        try {
            httpClient
                    .target("http", "localhost", portPayment)
                    .post("/stripe/sendEmail")
                    .withJsonPayload(payload)
                    .send(HttpResponse.BodyHandlers.ofString());
            LOG.info(() -> "Email sent successfully to: " + emailInputField);
        } catch (MicroserviceCallException e) {
            LOG.log(Level.SEVERE, "API call to send email failed. URI: " + e.getUri() + ", Status: " + e.getStatusCode(), e);
        } catch (IOException | InterruptedException e) {
            LOG.log(Level.SEVERE, "Could not send request to send email.", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            emailButtonIsDisabled = false;
        }
    }

    public void sendWelcomeEmailAfterCheckout(String email, String customerFullName, String hash) {
        String bodyEmail = "Hi " + customerFullName + "!\n\n"
                + "Thank you for signing up to Nocodefunctions with elevated access. Here's your link to use it:\n"
                + "https://nocodefunctions.com/?hash=" + hash + "\n\n"
                + "IDEAS AND BUGS\nIf you have feedback on Nocodefunctions, or come across any bugs, I'd love to hear it at analysis@exploreyourdata.com\n\n"
                + "DOWNLOAD INVOICES, SWITCH PLANS, PAUSE OR CANCEL SUBSCRIPTION\n"
                + "If you'd like to download invoices, switch plans, pause or cancel your subscription, you can do so on the site (click Billing in top right), or click the link below:\n"
                + "https://nocodefunctions.com/plans/billing.html?lang=" + sessionBean.getCurrentLocale().getLanguage() + "&hash=" + hash + "\n\n"
                + "Nocodefunctions is 100% self service. That means we can not send invoices, switch plans, pause or cancel your subscription for you but you can do it yourself easily.\n\n"
                + "Thank you for choosing our data analytics service—you’re in for exceptional insights.\n\n"
                + "- Clement (ExploreYourData)\nSolofounder of Nocodefunctions\nhttps://bsky.app/profile/seinecle.bsky.social";

        JsonObject payload = Json.createObjectBuilder()
                .add("emailTo", email)
                .add("emailFrom", "for-help-do-not-email-but-go-to-nocodefunctions-dot-com-click-billing@nocodefunctions.com")
                .add("subject", "Extra capacity for nocodefunctions")
                .add("serviceName", SingletonBean.SERVICE_NAME)
                .add("bodyEmail", bodyEmail)
                .build();
        try {
            httpClient
                    .target("http", "localhost", portPayment)
                    .post("/stripe/sendEmail")
                    .withJsonPayload(payload)
                    .send(HttpResponse.BodyHandlers.ofString());
            LOG.info("Welcome email sent successfully to: " + email);
        } catch (MicroserviceCallException e) {
            LOG.log(Level.SEVERE, "API call to send welcome email failed. URI: " + e.getUri() + ", Status: " + e.getStatusCode(), e);
        } catch (IOException | InterruptedException e) {
            LOG.log(Level.SEVERE, "Could not send request to send welcome email.", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public String getCheckoutUrlForServiceAndPrice(String priceId, String credits, String email) {
        JsonObject payload = Json.createObjectBuilder()
                .add("baseUrl", RemoteLocal.getDomain())
                .add("cancelPath", "/plans/canceled.html?stripe_session_id={CHECKOUT_SESSION_ID}")
                .add("successPath", "/index.html?stripe_session_id={CHECKOUT_SESSION_ID}")
                .add("priceId", priceId)
                .add("paymentMode", "subscription")
                .add("email", email)
                .build();
        try {
            HttpResponse<String> resp = httpClient
                    .target("http", "localhost", portPayment)
                    .post("/stripe/getCheckoutUrl")
                    .withJsonPayload(payload)
                    .send(HttpResponse.BodyHandlers.ofString());
            return resp.body();
        } catch (MicroserviceCallException e) {
            LOG.log(Level.SEVERE, "API call to get checkout URL failed. URI: " + e.getUri() + ", Status: " + e.getStatusCode(), e);
        } catch (IOException | InterruptedException e) {
            LOG.log(Level.SEVERE, "Could not send request to get checkout URL.", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return null;
    }

    public String getHashFromCustomerEmail(String customerEmail) {
        try {
            HttpResponse<String> resp = httpClient
                    .target("http", "localhost", portPayment)
                    .get("/stripe/getHashFromCustomerEmail")
                    .addQueryParameter("customerEmail", customerEmail)
                    .send(HttpResponse.BodyHandlers.ofString());
            return resp.body();
        } catch (MicroserviceCallException e) {
            LOG.log(Level.WARNING, () -> "API call to get hash from email failed, this is expected for new customers. URI: " + e.getUri() + ", Status: " + e.getStatusCode());
        } catch (IOException | InterruptedException e) {
            LOG.log(Level.SEVERE, "Could not send request to get hash from email.", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return null;
    }

    public String getEmailFromCustomerHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return null;
        }
        try {
            HttpResponse<String> resp = httpClient
                    .target("http", "localhost", portPayment)
                    .get("/stripe/getCustomerEmailByHash")
                    .addQueryParameter("hash", hash)
                    .send(HttpResponse.BodyHandlers.ofString());
            return resp.body();
        } catch (MicroserviceCallException e) {
            LOG.log(Level.SEVERE, "API call to get email from hash failed. URI: " + e.getUri() + ", Status: " + e.getStatusCode(), e);
        } catch (IOException | InterruptedException e) {
            LOG.log(Level.SEVERE, "Could not send request to get email from hash.", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return null;
    }

    public Customer getCustomerInfoBySessionId(String stripeSessionId) {
        try {
            HttpResponse<String> resp = httpClient
                    .target("http", "localhost", portPayment)
                    .get("/stripe/getCustomerInfoBySessionId")
                    .addQueryParameter("stripeSessionId", stripeSessionId)
                    .send(HttpResponse.BodyHandlers.ofString());
            Jsonb jb = JsonbBuilder.create();
            return jb.fromJson(resp.body(), Customer.class);
        } catch (MicroserviceCallException e) {
            LOG.log(Level.SEVERE, "API call to get customer info failed. URI: " + e.getUri() + ", Status: " + e.getStatusCode(), e);
        } catch (IOException | InterruptedException e) {
            LOG.log(Level.SEVERE, "Could not send request to get customer info.", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } catch (JsonbException e) {
            LOG.log(Level.SEVERE, "Failed to deserialize customer info.", e);
        }
        return null;
    }

    public Integer getRemainingCredits() {
        if (!sessionBean.isHashPresent()) {
            return 0;
        }
        String hash = sessionBean.getHash();
        try {
            HttpResponse<String> resp = httpClient
                    .target("http", "localhost", portPayment)
                    .get("/stripe/getCustomerRemainingCredits")
                    .addQueryParameter("hash", hash)
                    .addQueryParameter("serviceName", SingletonBean.SERVICE_NAME)
                    .send(HttpResponse.BodyHandlers.ofString());
            return Integer.valueOf(resp.body());
        } catch (MicroserviceCallException e) {
            LOG.log(Level.SEVERE, "API call to get remaining credits failed. URI: " + e.getUri() + ", Status: " + e.getStatusCode(), e);
        } catch (IOException | InterruptedException | NumberFormatException e) {
            System.out.println("Stripe service can't be reached");
//LOG.log(Level.SEVERE, "Could not process request for remaining credits.", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return 0;
    }

    public String getUrlCheckoutMonthlyProPlan() {
        return urlCheckoutMonthlyProPlan;
    }

    public void setUrlCheckoutMonthlyProPlan(String urlCheckoutMonthlyProPlan) {
        this.urlCheckoutMonthlyProPlan = urlCheckoutMonthlyProPlan;
    }

    public String getUrlCheckoutMonthlyPatronPlan() {
        return urlCheckoutMonthlyPatronPlan;
    }

    public void setUrlCheckoutMonthlyPatronPlan(String urlCheckoutMonthlyPatronPlan) {
        this.urlCheckoutMonthlyPatronPlan = urlCheckoutMonthlyPatronPlan;
    }

    public String getUrlParamStripeSessionIdReturnedByCheckout() {
        return urlParamStripeSessionIdReturnedByCheckout;
    }

    public void setUrlParamStripeSessionIdReturnedByCheckout(String urlParamStripeSessionIdReturnedByCheckout) {
        this.urlParamStripeSessionIdReturnedByCheckout = UrlParamCleaner.getRightmostPart(urlParamStripeSessionIdReturnedByCheckout);
    }

    public String getUrlParamEmail() {
        return urlParamEmail;
    }

    public void setUrlParamEmail(String urlParamEmail) {
        this.urlParamEmail = UrlParamCleaner.getRightmostPart(urlParamEmail);
    }

    public String getEmailInputField() {
        return emailInputField;
    }

    public void setEmailInputField(String emailInputField) {
        this.emailInputField = emailInputField;
    }

    public String getUrlBillingPortal() {
        return TEST ? BILLING_PORTAL_URL_TEST : BILLING_PORTAL_URL_PROD;
    }

    public void setRemainingCredits(Integer remainingCredits) {
        this.remainingCredits = remainingCredits;
    }

    public boolean isEligibleForRefund() {
        return eligibleForRefund;
    }

    public void setEligibleForRefund(boolean eligibleForRefund) {
        this.eligibleForRefund = eligibleForRefund;
    }

    public String getMAX_USED_CREDITS_FOR_REFUND_ELIGIBILITY() {
        return MAX_USED_CREDITS_FOR_REFUND_ELIGIBILITY;
    }

    public void setMAX_USED_CREDITS_FOR_REFUND_ELIGIBILITY(String MAX_USED_CREDITS_FOR_REFUND_ELIGIBILITY) {
        this.MAX_USED_CREDITS_FOR_REFUND_ELIGIBILITY = MAX_USED_CREDITS_FOR_REFUND_ELIGIBILITY;
    }

    public boolean isEmailButtonIsDisabled() {
        return emailButtonIsDisabled;
    }

    public void setEmailButtonIsDisabled(boolean emailButtonIsDisabled) {
        this.emailButtonIsDisabled = emailButtonIsDisabled;
    }
}
