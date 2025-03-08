/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.clementlevallois.nocodeapp.web.front.stripe;


import io.mikael.urlbuilder.UrlBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.servlet.annotation.MultipartConfig;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;

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
    private String nocredits = "";
    private boolean showAlertnoCredits = false;
    private boolean creditsCheckPerformed = false;
    private boolean eligibleForRefund = true;
    private boolean emailButtonIsDisabled = false;
    private static final String SERVICE_PAGE = "translate.html";
    private static final String PRICE_PRO_MONTHLY_TEST = "price_0Qwgx037XvgprGmlwZsa8pfS";
    private static final String PRICE_PATRON_MONTHLY_TEST = "price_0QzbmB37XvgprGmlzLwTUges";
    private static final String PRICE_PRO_MONTHLY = "";
    private static final String PRICE_PATRON_MONTHLY = "";
    private static final String MONTHLY_CREDITS_PRO = "50";
    private static final String MONTHLY_CREDITS_PATRON = "50";
    private String MAX_USED_CREDITS_FOR_REFUND_ELIGIBILITY = "5";
    private static final HttpClient client = HttpClient.newHttpClient();

    private int portPayment;

    private final boolean TEST = true;
    private static final String BILLING_PORTAL_URL_TEST = "https://billing.stripe.com/p/login/test_14k5og3L67BEf28eUU";
    private static final String BILLING_PORTAL_URL_PROD = "https://billing.stripe.com/p/login/dR6171e74flG8nK5kk";

    private static final Set<String> allPaidPlansForThisServiceTest = Set.of(PRICE_PRO_MONTHLY_TEST);
    private static final Set<String> allPaidPlansForThisService = Set.of(PRICE_PRO_MONTHLY);

    private Properties privateProperties;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    SessionBean sessionBean;

    @PostConstruct
    public void init() {
        privateProperties = privateProperties = applicationProperties.getPrivateProperties();
        portPayment = Integer.parseInt(privateProperties.getProperty("port-payment-ops"));
        creditsCheckPerformed = false;
    }

    public boolean isShowAlertnoCredits() {
        return nocredits.equals("true");
    }

    public void initializeRedirectState() {
        this.emailButtonIsDisabled = false;
    }

    public void setShowAlertnoCredits(boolean showAlertnoCredits) {
        this.showAlertnoCredits = showAlertnoCredits;
    }

//    public void decisionTreeBeforeRenderingIndexPage() {
//
//        FacesContext context = FacesContext.getCurrentInstance();
//
//        String hash = sessionBean.getHash();
//
//        // IF NO CREDIT
//        if (nocredits.equals("true")) {
//            return;
//        }
//
//        Customer customer = getCustomerByHash(hash);
//        if (customer != null) {
//            try {
//                context.getExternalContext().redirect(SERVICE_PAGE);
//                context.responseComplete(); // Stop JSF lifecycle after redirect
//            } catch (IOException ex) {
//                Logger.getLogger(StripeBean.class.getName()).log(Level.SEVERE, null, ex);
//            }
//            return;
//        }
//
//        // IF WE LAND ON INDEX FROM THE STRIPE CHECKOUT PAGE
//        if (isRedirectFromStripeCheckout()) {
//            Session stripeSession = getStripeSessionBySessionId(urlParamStripeSessionIdReturnedByCheckout);
//            if (stripeSession.getStatus().equals("complete")) {
//                String hashFromCustomerEmail = "";
//                while (hashFromCustomerEmail.isBlank()) {
//                    hashFromCustomerEmail = getHashFromCustomerEmail(stripeSession.getCustomerEmail());
//                    try {
//                        Thread.sleep(Duration.ofMillis(200));
//                    } catch (InterruptedException ex) {
//                        Logger.getLogger(StripeBean.class.getName()).log(Level.SEVERE, null, ex);
//                    }
//                }
//                sessionBean.setHash(hashFromCustomerEmail); // write it to the session
//                sendWelcomeEmailAfterCheckout(stripeSession.getCustomerEmail(), stripeSession.getCustomerDetails().getName(), hashFromCustomerEmail); // send Welcome email
//                try {
//                    context.getExternalContext().redirect(SERVICE_PAGE);
//                    context.responseComplete(); // Stop JSF lifecycle after redirect
//                } catch (IOException ex) {
//                    Logger.getLogger(StripeBean.class.getName()).log(Level.SEVERE, null, ex);
//                }
//            } else {
//                System.out.println("we should never land here because if the checkout was not complete we would have not landed on index.html but on a cancel.html page");
//                // this case should never happen
//            }
//        }
//    }
    public void populatePlanUrls() {
        boolean emailParamAbsent = urlParamEmail == null || urlParamEmail.isBlank();
        if (emailParamAbsent && (sessionBean.getHash() == null || sessionBean.getHash().isBlank())) {
            try {
                ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
                externalContext.redirect(externalContext.getRequestContextPath() + "/" + "index.html?#pricing");
            } catch (IOException ex) {
                Logger.getLogger(StripeBean.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            if (emailParamAbsent) {
                String emailFromHash = getEmailFromCustomerHash(sessionBean.getHash());
                urlParamEmail = emailFromHash;
            }
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
        try {

            if (hash == null || hash.isBlank()) {
                String billingPortalURL = TEST ? BILLING_PORTAL_URL_TEST : BILLING_PORTAL_URL_PROD;
                ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
                externalContext.redirect(billingPortalURL);
            } else {
                URI uri = UrlBuilder
                        .empty()
                        .withScheme("http")
                        .withPort(portPayment)
                        .withHost("localhost")
                        .withPath("/externalapi/getStripeBillingUrl")
                        .addParameter("hash", hash)
                        .addParameter("successUrl", RemoteLocal.getDomain() + "/index.html?hash=" + hash)
                        .addParameter("lang", sessionBean.getCurrentLocale().getLanguage())
                        .toUri();

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .GET()
                        .uri(uri)
                        .build();

                HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) {
                    String stringError = resp.body();
                    System.out.println("stringError: " + stringError);
                    System.out.println("status code: " + resp.statusCode());
                } else {
                    String response = resp.body();
                    if (response.equals("{}")) {
                        System.out.println("no billing url returned");
                    } else {
                        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
                        externalContext.redirect(response);
                    }

                }
            }
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(StripeBean.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public Boolean isRedirectFromStripeCheckout() {
        return urlParamStripeSessionIdReturnedByCheckout != null && !urlParamStripeSessionIdReturnedByCheckout.isBlank();
    }

//    public Customer getCustomerByHash(String hash) {
//
//        if (hash == null || hash.isBlank()) {
//            System.out.println("hash was null or blank, we don't fetch a customer");
//            return null;
//        }
//        System.out.println("customer to be retrieved from hash: " + hash);
//
//        try {
//
//            URI uri = UrlBuilder
//                    .empty()
//                    .withScheme("http")
//                    .withPort(portPayment)
//                    .withHost("localhost")
//                    .withPath("/externalapi/getCustomerByHash")
//                    .addParameter("hash", hash)
//                    .toUri();
//
//            HttpRequest httpRequest = HttpRequest.newBuilder()
//                    .GET()
//                    .uri(uri)
//                    .build();
//
//            HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
//            if (resp.statusCode() != 200) {
//                String stringError = resp.body();
//                System.out.println("stringError: " + stringError);
//                System.out.println("status code: " + resp.statusCode());
//                return null;
//            } else {
//                String json = resp.body();
//                if (json.equals("{}")) {
////                    System.out.println("no customer for this hash");
//                    return null;
//                } else {
//                    Jsonb jsonb = JsonbBuilder.create();
//                    Customer currentCustomer = jsonb.fromJson(resp.body(), Customer.class);
//                    System.out.println("customer for this hash exists: " + currentCustomer.getEmail());
//                    return currentCustomer;
//                }
//
//            }
//        } catch (IOException | InterruptedException ex) {
//            Logger.getLogger(StripeBean.class.getName()).log(Level.SEVERE, null, ex);
//        }
//        return null;
//
//    }
    public void addHashFieldAndCreditsToCustomer(String customerId, String hash, String serviceName, String credits) {
        try {

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(portPayment)
                    .withHost("localhost")
                    .withPath("/externalapi/addHashFieldAndCreditsToCustomer")
                    .addParameter("hash", hash)
                    .addParameter("credits", credits)
                    .addParameter("customerId", customerId)
                    .addParameter("serviceName", serviceName)
                    .toUri();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .GET()
                    .uri(uri)
                    .build();

            HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                String stringError = resp.body();
                System.out.println("stringError: " + stringError);
                System.out.println("status code: " + resp.statusCode());
            }

        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(StripeBean.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public String anyRemainingCreditsBeforeServiceUse() {

        if (creditsCheckPerformed) {
            return "";
        }

        String hash = sessionBean.getHash();
        if (hash.isBlank()) {
            return "index.html?faces-redirect=true";
        }

        remainingCredits = getRemainingCredits();
        creditsCheckPerformed = true;
        if (remainingCredits < 1) {
            return "index.html?nocredits=true&faces-redirect=true";
        } else {
            nocredits = "";
        }
        return "";
    }

    public String handleRefund() {
        String hash = sessionBean.getHash();
        proceedToRefund(hash);
        return "/index.html";
    }

//    public Boolean checkRefundEligibility() {
//        String hash = sessionBean.getHash();
//        Customer customerByHash = getCustomerByHash(hash);
//        Map<String, String> metadata = customerByHash.getMetadata();
//
//        String usedCreditsOverall = metadata.getOrDefault(SERVICE_NAME_ALL_CREDITS_USED, "0");
//        eligibleForRefund = Integer.valueOf(usedCreditsOverall) <= Integer.valueOf(MAX_USED_CREDITS_FOR_REFUND_ELIGIBILITY);
//        return eligibleForRefund;
//    }
    public String proceedToRefund(String hashValueFromUrlParam) {
        try {
            String priceIds;
            if (TEST) {
                priceIds = String.join(";", allPaidPlansForThisServiceTest);
            } else {
                priceIds = String.join(";", allPaidPlansForThisService);
            }
            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(portPayment)
                    .withHost("localhost")
                    .withPath("/externalapi/proceedToRefund")
                    .addParameter("hash", hashValueFromUrlParam)
                    .addParameter("priceIds", priceIds)
                    .addParameter("serviceName", SingletonBean.serviceName)
                    .toUri();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .GET()
                    .uri(uri)
                    .build();

            HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                String stringError = resp.body();
                System.out.println("stringError: " + stringError);
                System.out.println("status code: " + resp.statusCode());
                return null;
            } else {
                System.out.println("refund successful");
            }

        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(StripeBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    public String resetServiceMetadataForThisCustomer(String hashValueFromUrlParam, String serviceName) {
        try {
            String priceIds;
            if (TEST) {
                priceIds = String.join(";", allPaidPlansForThisServiceTest);
            } else {
                priceIds = String.join(";", allPaidPlansForThisService);
            }
            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(portPayment)
                    .withHost("localhost")
                    .withPath("/externalapi/resetServiceMetadataForThisCustomer")
                    .addParameter("hash", hashValueFromUrlParam)
                    .addParameter("serviceName", serviceName)
                    .toUri();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .GET()
                    .uri(uri)
                    .build();

            HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                String stringError = resp.body();
                System.out.println("stringError: " + stringError);
                System.out.println("status code: " + resp.statusCode());
                return null;
            } else {
                System.out.println("refund successful");
            }

        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(StripeBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    public void manageCredits() {
        String hash = sessionBean.getHash();
        if (hash.isBlank()) {
            return;
        }
        try {
            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(portPayment)
                    .withHost("localhost")
                    .withPath("/externalapi/manageCredits")
                    .addParameter("hash", hash)
                    .addParameter("serviceName", SingletonBean.serviceName)
                    .addParameter("serviceNameAllCreditsUsed", SingletonBean.SERVICE_NAME_ALL_CREDITS_USED)
                    .toUri();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .GET()
                    .uri(uri)
                    .build();

            HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                String stringError = resp.body();
                System.out.println("stringError: " + stringError);
                System.out.println("status code: " + resp.statusCode());
            } else {
                remainingCredits = Integer.valueOf(resp.body());

            }

        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(StripeBean.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void updateCustomerWithMetadata(String customerId, Map<String, String> keyValuesToUpdate) {
        try {

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(portPayment)
                    .withHost("localhost")
                    .withPath("/externalapi/updateCustomerMetadata")
                    .addParameter("customerId", customerId)
                    .toUri();

            JsonObjectBuilder parametersBuilder = Json.createObjectBuilder();
            for (Map.Entry<String, String> entry : keyValuesToUpdate.entrySet()) {
                parametersBuilder.add(entry.getKey(), entry.getValue());
            }

            String jsonString = parametersBuilder.build().toString();

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(jsonString.getBytes(StandardCharsets.UTF_8));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();

            HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                String stringError = resp.body();
                System.out.println("stringError: " + stringError);
                System.out.println("status code: " + resp.statusCode());
            } else {
                System.out.println("key values sent to update customer metadata");
            }
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(StripeBean.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public String sendPricingInfoOrLoginUrlViaEmail() {
        if (emailInputField == null || emailInputField.isBlank()) {
            return "";
        }

        emailButtonIsDisabled = true;
        String hash = getHashFromCustomerEmail(emailInputField);

        String subject;
        String bodyEmail;

        if (hash != null & !hash.isBlank()) {

            remainingCredits = getRemainingCredits();
            creditsCheckPerformed = true;
            if (remainingCredits < 1) {
                return "pricing_table.html?hash=" + hash + "&faces-redirect=true";
            } else {
                subject = "👋 Your login link to Nocodefunctions";
                bodyEmail = """
                        Hi!
                        
                        This is your login link to Nocodefunctions: """ + RemoteLocal.getDomain() + "/?hash=" + hash + """
                                                                                                         
                                                         
                                                         Enjoy nocodefunctions!
                                                         
                                                         """;

            }
        } else {
            String urlPricingPlans = RemoteLocal.getDomain() + "/pricing_table.html?origin=email&email=" + emailInputField;
            subject = "👋 **ACTION REQUIRED** Sign up to Nocodefunctions";
            bodyEmail = """
                          Hi!
                          
                          To continue signing up to Nocodefunctions, go here:
                          
                          """
                    + urlPricingPlans + """
                                      
                                      
                                      """;
        }

        try {

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(portPayment)
                    .withHost("localhost")
                    .withPath("/externalapi/sendEmail")
                    .toUri();

            JsonObjectBuilder parametersBuilder = Json.createObjectBuilder();
            parametersBuilder.add("emailTo", emailInputField);
            parametersBuilder.add("emailFrom", "for-help-do-not-email-but-go-to-nocodefunctions-dot-com-click-billing@nocodefunctions.com");
            parametersBuilder.add("subject", subject);
            parametersBuilder.add("bodyEmail", bodyEmail);
            parametersBuilder.add("serviceName", SingletonBean.serviceName);

            String jsonString = parametersBuilder.build().toString();

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(jsonString.getBytes(StandardCharsets.UTF_8));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();

            HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                String stringError = resp.body();
                System.out.println("stringError: " + stringError);
                System.out.println("status code: " + resp.statusCode());
            } else {
                System.out.println("email sent with a login link");
            }
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(StripeBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        emailButtonIsDisabled = false;
        return "";
    }

    public void sendWelcomeEmailAfterCheckout(String email, String customerFullName, String hash) {
        try {

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(portPayment)
                    .withHost("localhost")
                    .withPath("/externalapi/sendEmail")
                    .toUri();

            JsonObjectBuilder parametersBuilder = Json.createObjectBuilder();
            parametersBuilder.add("emailTo", email);
            parametersBuilder.add("emailFrom", "for-help-do-not-email-but-go-to-nocodefunctions-dot-com-click-billing@nocodefunctions.com");
            parametersBuilder.add("subject", "Extra capacity for nocodefunctions");

            String bodyEmail = """
Hi """ + " " + customerFullName + "! " + """

                                   
Thank you for signing up to Nocodefunctions with elevated access. Here's your link to use it:
https://nocodefunctions.com/?hash=""" + hash + """

                                         
IDEAS AND BUGS
If you have feedback on Nocodefunctions, or come across any bugs, I'd love to hear it at analysis@exploreyourdata.com

                                         
DOWNLOAD INVOICES, SWITCH PLANS, PAUSE OR CANCEL SUBSCRIPTION
If you'd like to download invoices, switch plans, pause or cancel your subscription, you can do so on the site (click Billing in top left), or click the link below:
https://nocodefunctions.com/billing.html?lang=""" + sessionBean.getCurrentLocale().getLanguage() + "&hash=" + hash + """

                                                
Nocodefuctions is 100% self service. That means we can not send invoices, switch plans, pause or cancel your subscription for you but you can do it yourself easily.

Thank you for choosing our data analytics service—you’re in for exceptional insights.

- Clement (ExploreYourData)
Solofounder of Nocodefunctions
https://bsky.app/profile/seinecle.bsky.social""";

            parametersBuilder.add("bodyEmail", bodyEmail);

            String jsonString = parametersBuilder.build().toString();

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(jsonString.getBytes(StandardCharsets.UTF_8));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();

            HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                String stringError = resp.body();
                System.out.println("stringError: " + stringError);
                System.out.println("status code: " + resp.statusCode());
            } else {
                System.out.println("email sent with a login link");
            }
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(StripeBean.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public String getCheckoutUrlForServiceAndPrice(String priceId, String credits, String email) {

        URI uri = UrlBuilder
                .empty()
                .withScheme("http")
                .withPort(portPayment)
                .withHost("localhost")
                .withPath("/externalapi/getCheckoutUrl")
                .toUri();

        JsonObjectBuilder parametersBuilder = Json.createObjectBuilder();
        parametersBuilder.add("baseUrl", RemoteLocal.getDomain());
        parametersBuilder.add("cancelPath", "/canceled.html?stripe_session_id={CHECKOUT_SESSION_ID}");
        parametersBuilder.add("successPath", "/index.html?stripe_session_id={CHECKOUT_SESSION_ID}");
        parametersBuilder.add("priceId", priceId);
        parametersBuilder.add("paymentMode", "subscription");
        parametersBuilder.add("email", email);

        String jsonString = parametersBuilder.build().toString();

        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(jsonString.getBytes(StandardCharsets.UTF_8));

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .POST(bodyPublisher)
                .uri(uri)
                .build();

        try {
            HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                String stringError = resp.body();
                System.out.println("stringError: " + stringError);
                System.out.println("status code: " + resp.statusCode());
            } else {
                String url = resp.body();
                return url;
            }
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(StripeBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("reached the end of the method without a returned url");
        return null;
    }

    public String getHashFromCustomerEmail(String customerEmail) {

        URI uri = UrlBuilder
                .empty()
                .withScheme("http")
                .withPort(portPayment)
                .withHost("localhost")
                .withPath("/externalapi/getHashFromCustomerEmail")
                .addParameter("customerEmail", customerEmail)
                .toUri();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(uri)
                .build();

        try {
            HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                String stringError = resp.body();
                System.out.println("stringError: " + stringError);
                System.out.println("status code: " + resp.statusCode());
            } else {
                return resp.body();
            }
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(StripeBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("reached the end of the method without a returned hash");
        return null;
    }

    public String getEmailFromCustomerHash(String hash) {

        URI uri = UrlBuilder
                .empty()
                .withScheme("http")
                .withPort(portPayment)
                .withHost("localhost")
                .withPath("/externalapi/getCustomerEmailByHash")
                .addParameter("hash", hash)
                .toUri();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(uri)
                .build();

        try {
            HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                String stringError = resp.body();
                System.out.println("stringError: " + stringError);
                System.out.println("status code: " + resp.statusCode());
            } else {
                return resp.body();
            }
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(StripeBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("reached the end of the method without a returned email");
        return null;
    }

//    public Session getStripeSessionBySessionId(String stripeSessionId) {
//
//        URI uri = UrlBuilder
//                .empty()
//                .withScheme("http")
//                .withPort(portPayment)
//                .withHost("localhost")
//                .withPath("/externalapi/getStripeSessionBySessionId")
//                .addParameter("stripeSessionId", stripeSessionId)
//                .toUri();
//
//        HttpRequest httpRequest = HttpRequest.newBuilder()
//                .uri(uri)
//                .build();
//
//        try {
//            HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
//            if (resp.statusCode() != 200) {
//                String stringError = resp.body();
//                System.out.println("stringError: " + stringError);
//                System.out.println("status code: " + resp.statusCode());
//            } else {
//                String json = resp.body();
//                if (json.equals("{}")) {
//                    return null;
//                } else {
//                    Jsonb jsonb = JsonbBuilder.create();
//                    Session session = jsonb.fromJson(resp.body(), Session.class);
//
//                    return session;
//
//                }
//            }
//        } catch (IOException | InterruptedException ex) {
//            Logger.getLogger(StripeBean.class.getName()).log(Level.SEVERE, null, ex);
//        }
//        System.out.println("reached the end of the method without a returned session");
//        return null;
//    }
//    public LineItem getLineItemsFromCheckoutSession(String stripeSessionId) {
//
//        URI uri = UrlBuilder
//                .empty()
//                .withScheme("http")
//                .withPort(portPayment)
//                .withHost("localhost")
//                .withPath("/externalapi/getLineItemsFromCheckoutSession")
//                .addParameter("stripeSessionId", stripeSessionId)
//                .toUri();
//
//        HttpRequest httpRequest = HttpRequest.newBuilder()
//                .uri(uri)
//                .build();
//
//        try {
//            HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
//            if (resp.statusCode() != 200) {
//                String stringError = resp.body();
//                System.out.println("stringError: " + stringError);
//                System.out.println("status code: " + resp.statusCode());
//            } else {
//                String json = resp.body();
//                if (json.equals("{}")) {
//                    return null;
//                } else {
//                    Jsonb jsonb = JsonbBuilder.create();
//                    LineItem lineItem = jsonb.fromJson(resp.body(), LineItem.class);
//                    return lineItem;
//
//                }
//            }
//        } catch (IOException | InterruptedException ex) {
//            Logger.getLogger(StripeBean.class.getName()).log(Level.SEVERE, null, ex);
//        }
//        System.out.println("reached the end of the method without a returned session");
//        return null;
//    }
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
        this.urlParamStripeSessionIdReturnedByCheckout = urlParamStripeSessionIdReturnedByCheckout;
    }

    public String getUrlParamEmail() {
        return urlParamEmail;
    }

    public void setUrlParamEmail(String urlParamEmail) {
        this.urlParamEmail = urlParamEmail;
    }

    public String getEmailInputField() {
        return emailInputField;
    }

    public void setEmailInputField(String emailInputField) {
        this.emailInputField = emailInputField;
    }

    public String getUrlBillingPortal() {
        String billingPortalURL = TEST ? BILLING_PORTAL_URL_TEST : BILLING_PORTAL_URL_PROD;
        return billingPortalURL;
    }

    public Integer getRemainingCredits() {
        String hash = sessionBean.getHash();
        if (hash.isBlank()) {
            return 0;
        }

        URI uri = UrlBuilder
                .empty()
                .withScheme("http")
                .withPort(portPayment)
                .withHost("localhost")
                .withPath("/externalapi/getCustomerRemainingCredits")
                .addParameter("hash", hash)
                .addParameter("serviceName", SingletonBean.serviceName)
                .toUri();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(uri)
                .build();

        try {
            HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                String stringError = resp.body();
                System.out.println("stringError: " + stringError);
                System.out.println("status code: " + resp.statusCode());
                remainingCredits = 0;
            } else {
                remainingCredits = Integer.valueOf(resp.body());
            }
        } catch (IOException | InterruptedException | NumberFormatException e) {
            System.out.println("error retrieving remaining credits");
            remainingCredits = 0;
        }

        return remainingCredits;
    }

    public void setRemainingCredits(Integer remainingCredits) {
        this.remainingCredits = remainingCredits;
    }

    public String getNocredits() {
        return nocredits;
    }

    public void setNocredits(String nocredits) {
        this.nocredits = nocredits;
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
