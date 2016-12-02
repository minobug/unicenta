package com.openbravo.pos.eet;

import com.openbravo.pos.ticket.TicketInfo;
import cz.etrzby.xml.TrzbaDataType;
import cz.tomasdvorak.eet.client.EETClient;
import cz.tomasdvorak.eet.client.EETServiceFactory;
import cz.tomasdvorak.eet.client.config.CommunicationMode;
import cz.tomasdvorak.eet.client.config.EndpointType;
import cz.tomasdvorak.eet.client.config.SubmissionType;
import cz.tomasdvorak.eet.client.dto.SubmitResult;
import cz.tomasdvorak.eet.client.exceptions.CommunicationException;
import cz.tomasdvorak.eet.client.exceptions.DataSigningException;
import cz.tomasdvorak.eet.client.exceptions.InvalidKeystoreException;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Properties;

/**
 * Created by md on 2016-11-29.
 */
public class EetClient {

    private static final org.apache.log4j.Logger LOG = org.apache.log4j.Logger.getLogger(EetClient.class);
    public static final String PROPERTIES_KEY = "eet.properties";

    private String dic;
    private int provozovna;
    private String pokladna;
    private EndpointType endpointType;
    private EETClient client;

    public void init(Properties properties) {

        LOG.debug("Reading properties...");

        dic = properties.getProperty("dic");
        provozovna = Integer.parseInt(properties.getProperty("provozovna"));
        pokladna = properties.getProperty("pokladna");
        boolean isProduction = Boolean.parseBoolean(properties.getProperty("production"));
        endpointType = isProduction ? EndpointType.PRODUCTION : EndpointType.PLAYGROUND;

        String clientKeyPassword = properties.getProperty("client.key.password");
        String clientKeyFilePath = properties.getProperty("client.key.filepath");
        String serverCa1CertFilePath = properties.getProperty("server.ca.cert1.filepath");
        String serverCa2CertFilePath = properties.getProperty("server.ca.cert2.filepath");

        try {
            LOG.info("Initializing...");

            InputStream clientKey = new FileInputStream(clientKeyFilePath);

            InputStream[] serverCertChain = isProduction ?
                    new InputStream[]{new FileInputStream(serverCa1CertFilePath), new FileInputStream(serverCa2CertFilePath)}
                    : new InputStream[]{new FileInputStream(serverCa1CertFilePath)};

            client = EETServiceFactory.getInstance(clientKey, clientKeyPassword, serverCertChain);
        } catch (FileNotFoundException | InvalidKeystoreException e) {
            LOG.error("Initialization failed", e);
            throw new RuntimeException("EET Client initialization failed", e);
        }
    }

    public void sendToEet(TicketInfo ticket) {

        TrzbaDataType data = new TrzbaDataType()
                .withDicPopl(dic)
                .withIdProvoz(provozovna)
                .withIdPokl(pokladna)
                .withPoradCis(Integer.toString(ticket.getTicketId()))
                .withDatTrzby(ticket.getDate())
                .withCelkTrzba(new BigDecimal(ticket.getTotalPaid()));

        try {

            SubmitResult result = client.submitReceipt(data, CommunicationMode.REAL, endpointType, SubmissionType.FIRST_ATTEMPT);

            // print FIK and BKP on the receipt
            ticket.setProperty("fik", result.getFik());
            ticket.setProperty("bkp", result.getBKP());

            LOG.info("EET SUCCESS ticket#: " + ticket.getTicketId());
            LOG.debug(String.format("EET SUCCESS2 date: %s, total: %s,  FIK: %s; BKP: %s", ticket.getDate(), ticket.getTotal(), result.getFik(), result.getBKP()));

        } catch (final CommunicationException e) {

            // print PKP on the receipt
            ticket.setProperty("pkp", e.getPKP());

            LOG.error(String.format("EET FAILURE ticket#: %s, date: %s, total: %s, ", ticket.getTicketId(), ticket.getDate(), ticket.getTotal()));
            LOG.debug(e.getPKP());

        } catch (DataSigningException e) {
            LOG.error(String.format("EET SIGNING FAILURE ticket#: %s, date: %s, total: %s, ", ticket.getTicketId(), ticket.getDate(), ticket.getTotal()));
            LOG.error("EET SIGNING FAILURE", e);
        }
    }
}
