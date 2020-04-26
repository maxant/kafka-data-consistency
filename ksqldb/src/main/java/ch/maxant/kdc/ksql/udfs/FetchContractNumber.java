package ch.maxant.kdc.ksql.udfs;

import io.confluent.ksql.function.udf.Udf;
import io.confluent.ksql.function.udf.UdfDescription;
import io.confluent.ksql.function.udf.UdfParameter;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

@UdfDescription(name = "fetchContractNumber", description = "Fetches a contract number. uses a cache.")
public class FetchContractNumber {

    private static final Set<String> AVAILABLE_CONTRACT_NUMBERS = new HashSet<>(1000);

    @Udf(description = "As per description")
    public String doit(@UdfParameter(value = "contractId") String contractId) {
        System.out.println("==============================================================================");
        return getContractNumber(contractId);
    }

    private String getContractNumber(String contractId) {
        synchronized (AVAILABLE_CONTRACT_NUMBERS) {
            if (AVAILABLE_CONTRACT_NUMBERS.size() < 1) {
                refillContractNumbers();
            }
            String contractNumber = AVAILABLE_CONTRACT_NUMBERS.iterator().next();
            AVAILABLE_CONTRACT_NUMBERS.remove(contractNumber);
            System.out.println("fetchContractNumber fetched " + contractNumber + " for contractId " + contractId);
            return contractNumber;
        }
    }

    private void refillContractNumbers() {
        // TODO URL needs to be configurable!
        System.out.println("fetchContractNumber refilling contractNumbers from number system...");
        long start = System.currentTimeMillis();
        String u = "http://kdc.numbers.maxant.ch/numbers?type=contract&count=1000";
        try {
            URL url = new URL(u);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestMethod("POST");
            con.setConnectTimeout(50000);
            con.setReadTimeout(50000);
            int status = con.getResponseCode();
            if (status == 201) {
                try {
                    try (Scanner scanner = new Scanner(con.getInputStream(), StandardCharsets.UTF_8.name())) {
                        String json = scanner.useDelimiter("\\A").next();
                        json = json.replace("[", "");
                        json = json.replace("]", "");
                        json = json.replace(" ", "");
                        for (String contractNumber : json.split(",")) {
                            AVAILABLE_CONTRACT_NUMBERS.add(contractNumber);
                        }
                        System.out.println("fetchContractNumber fetched numbers in " + (System.currentTimeMillis() - start) + "ms");
                    }
                } finally {
                    con.getInputStream().close();
                }
            } else {
                throw new RuntimeException("error in connection " + url + ": " + status);
            }
        } catch (Exception e) {
            throw new RuntimeException("error in connection " + u + ": " + e.getMessage(), e);
        }
    }
}
