// SPDX-License-Identifier: MIT
package com.mercedesbenz.sechub.domain.scan.product.pds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mercedesbenz.sechub.adapter.DefaultExecutorConfigSupport;
import com.mercedesbenz.sechub.commons.core.util.SecHubStorageUtil;
import com.mercedesbenz.sechub.commons.core.util.SimpleStringUtils;
import com.mercedesbenz.sechub.commons.pds.PDSConfigDataKeyProvider;
import com.mercedesbenz.sechub.commons.pds.PDSDefaultParameterKeyConstants;
import com.mercedesbenz.sechub.commons.pds.PDSKey;
import com.mercedesbenz.sechub.commons.pds.PDSKeyProvider;
import com.mercedesbenz.sechub.domain.scan.NetworkTargetProductServerDataProvider;
import com.mercedesbenz.sechub.domain.scan.NetworkTargetType;
import com.mercedesbenz.sechub.domain.scan.config.ScanMapping;
import com.mercedesbenz.sechub.domain.scan.product.config.ProductExecutorConfig;
import com.mercedesbenz.sechub.sharedkernel.configuration.SecHubConfiguration;
import com.mercedesbenz.sechub.sharedkernel.error.NotAcceptableException;
import com.mercedesbenz.sechub.sharedkernel.validation.Validation;

public class PDSExecutorConfigSuppport extends DefaultExecutorConfigSupport implements NetworkTargetProductServerDataProvider, ReuseSecHubStorageInfoProvider {

    private static final Logger LOG = LoggerFactory.getLogger(PDSExecutorConfigSuppport.class);

    public static final String PARAM_ID = "pds.executor.config.support";

    private static final List<PDSKeyProvider<?>> keyProvidersForSendingParametersToPDS;
    private static final List<PDSKeyProvider<?>> keyProvidersForReusingStorageDetectionOnly;

    private PDSExecutorConfigSuppportServiceCollection serviceCollection;

    static {
        List<PDSKeyProvider<?>> allParameterProviders = new ArrayList<>();
        allParameterProviders.addAll(Arrays.asList(SecHubProductExecutionPDSKeyProvider.values()));
        allParameterProviders.addAll(Arrays.asList(PDSConfigDataKeyProvider.values()));

        keyProvidersForSendingParametersToPDS = Collections.unmodifiableList(allParameterProviders);

        List<PDSConfigDataKeyProvider> onlySecHubStorageUseProviderList = Collections.singletonList(PDSConfigDataKeyProvider.PDS_CONFIG_USE_SECHUB_STORAGE);
        keyProvidersForReusingStorageDetectionOnly = Collections.unmodifiableList(onlySecHubStorageUseProviderList);
    }

    public static List<PDSKeyProvider<? extends PDSKey>> getUnmodifiableListOfParameterKeyProvidersSentToPDS() {
        return keyProvidersForSendingParametersToPDS;
    }

    /**
     * Creates the configuration support and VALIDATE. This will fail when
     * configuration data is not valid (e.g. mandatory keys missing)
     *
     * @param config
     * @param systemEnvironment
     * @return support
     * @throws NotAcceptableException when configuration is not valid
     */
    public static PDSExecutorConfigSuppport createSupportAndAssertConfigValid(ProductExecutorConfig config,
            PDSExecutorConfigSuppportServiceCollection serviceCollection) {
        return new PDSExecutorConfigSuppport(config, serviceCollection, new PDSProductExecutorMinimumConfigValidation());
    }

    private PDSExecutorConfigSuppport(ProductExecutorConfig config, PDSExecutorConfigSuppportServiceCollection serviceCollection,
            Validation<ProductExecutorConfig> validation) {
        super(config, serviceCollection.getSystemEnvironmentVariableSupport(), validation);
        this.serviceCollection = serviceCollection;
    }

    public Map<String, String> createJobParametersToSendToPDS(SecHubConfiguration secHubConfiguration) {

        Map<String, String> parametersToSend = createParametersToSendByProviders(keyProvidersForSendingParametersToPDS);

        /* provide SecHub storage when necessary */
        if (isReusingSecHubStorage(parametersToSend)) {
            String projectId = secHubConfiguration.getProjectId();
            String sechubStoragePath = SecHubStorageUtil.createStoragePath(projectId);

            parametersToSend.put(PDSDefaultParameterKeyConstants.PARAM_KEY_PDS_CONFIG_SECHUB_STORAGE_PATH, sechubStoragePath);
        }
        addMappingsAsJobParameter(parametersToSend);
        return parametersToSend;
    }

    private void addMappingsAsJobParameter(Map<String, String> parametersToSend) {
        String useSecHubMappingsEntry = parametersToSend.get(PDSDefaultParameterKeyConstants.PARAM_KEY_PDS_CONFIG_USE_SECHUB_MAPPINGS);

        List<String> mappingIds = SimpleStringUtils.createListForCommaSeparatedValues(useSecHubMappingsEntry);
        for (String mappingId : mappingIds) {
            if (parametersToSend.containsKey(mappingId)) {
                LOG.warn("Cannot use mapping id: {} because already used as mapping entry by config. Will skip this one.");
                continue;
            }
            Optional<ScanMapping> scanMapping = serviceCollection.getScanMappingRepository().findById(mappingId);
            if (scanMapping.isPresent()) {
                String mappingDataJson = scanMapping.get().getData();
                parametersToSend.put(mappingId, mappingDataJson);
            } else {
                LOG.warn("Configuration wants to use sechub mapping {}, but mapping is not found! Fallback to empty JSON.", mappingId);
                parametersToSend.put(mappingId, "{}"); // add empty JSON
            }
        }
    }

    private Map<String, String> createParametersToSendByProviders(List<PDSKeyProvider<?>> providers) {
        Map<String, String> parametersToSend = new TreeMap<>();
        for (String originKey : configuredExecutorParameters.keySet()) {
            PDSKeyProvider<?> foundProvider = null;
            for (PDSKeyProvider<?> provider : providers) {
                String key = provider.getKey().getId();
                if (originKey.equalsIgnoreCase(key)) {
                    foundProvider = provider;
                    break;
                }
            }
            /* either not special (so always sent to PDS) or special but must be sent */
            if (foundProvider == null || foundProvider.getKey().isSentToPDS()) {
                parametersToSend.put(originKey, configuredExecutorParameters.get(originKey));
            }
        }
        return parametersToSend;
    }

    public boolean isReusingSecHubStorage() {
        return isReusingSecHubStorage(createParametersToSendByProviders(keyProvidersForReusingStorageDetectionOnly));
    }

    static boolean isReusingSecHubStorage(Map<String, String> parametersToSend) {
        String useSecHubStorage = parametersToSend.get(PDSDefaultParameterKeyConstants.PARAM_KEY_PDS_CONFIG_USE_SECHUB_STORAGE);
        return Boolean.parseBoolean(useSecHubStorage);
    }

    public String getPDSProductIdentifier() {
        return getParameter(PDSConfigDataKeyProvider.PDS_CONFIG_PRODUCTIDENTIFIER);
    }

    public int getTimeToWaitForNextCheckOperationInMilliseconds(PDSInstallSetup setup) {
        int value = getParameterIntValue(SecHubProductExecutionPDSKeyProvider.TIME_TO_WAIT_FOR_NEXT_CHECKOPERATION_IN_MILLISECONDS);
        if (value != -1) {
            return value;
        }
        /* fallback to setup */
        return setup.getDefaultTimeToWaitForNextCheckOperationInMilliseconds();
    }

    public int getTimeoutInMinutes(PDSInstallSetup setup) {
        int value = getParameterIntValue(SecHubProductExecutionPDSKeyProvider.TIME_TO_WAIT_BEFORE_TIMEOUT_IN_MINUTES);
        if (value != -1) {
            return value;
        }
        /* fallback to setup */
        return setup.getDefaultTimeOutInMinutes();
    }

    /**
     * @return <code>true</code> when PDS server with an untrusted certificate (e.g.
     *         self signed) is accepted, <code>false</code> when not (default)
     */
    public boolean isTrustAllCertificatesEnabled() {
        return getParameterBooleanValue(SecHubProductExecutionPDSKeyProvider.TRUST_ALL_CERTIFICATES);
    }

    public boolean isTargetTypeForbidden(NetworkTargetType targetType) {
        boolean forbidden = false;
        for (SecHubProductExecutionPDSKeyProvider provider : SecHubProductExecutionPDSKeyProvider.values()) {
            if (forbidden) {
                break;
            }
            PDSKey forbiddenKey = provider.getKey();
            if (!(forbiddenKey instanceof ForbiddenTargetTypePDSKey)) {
                continue;
            }
            ForbiddenTargetTypePDSKey pdsForbiddenKey = (ForbiddenTargetTypePDSKey) forbiddenKey;
            if (!targetType.equals(pdsForbiddenKey.getForbiddenTargetType())) {
                continue;
            }
            String val = getParameter(forbiddenKey);
            forbidden = Boolean.parseBoolean(val);
        }
        return forbidden;
    }

    private String getParameter(PDSKeyProvider<?> keyProvider) {
        return getParameter(keyProvider.getKey());
    }

    private String getParameter(PDSKey configDataKey) {
        return getParameter(configDataKey.getId());
    }

    private int getParameterIntValue(PDSKeyProvider<? extends PDSKey> provider) {
        return getParameterIntValue(provider.getKey().getId());
    }

    private boolean getParameterBooleanValue(PDSKeyProvider<? extends PDSKey> provider) {
        return getParameterBooleanValue(provider.getKey().getId());
    }

    @Override
    public String getIdentifierWhenInternetTarget() {
        return config.getName();
    }

    @Override
    public String getIdentifierWhenIntranetTarget() {
        return config.getName();
    }

    @Override
    public String getBaseURLWhenInternetTarget() {
        return getProductBaseURL();
    }

    @Override
    public String getBaseURLWhenIntranetTarget() {
        return getProductBaseURL();
    }

    @Override
    public String getUsernameWhenInternetTarget() {
        return getUser();
    }

    @Override
    public String getUsernameWhenIntranetTarget() {
        return getUser();
    }

    @Override
    public String getPasswordWhenInternetTarget() {
        return getPasswordOrAPIToken();
    }

    @Override
    public String getPasswordWhenIntranetTarget() {
        return getPasswordOrAPIToken();
    }

    @Override
    public boolean hasUntrustedCertificateWhenIntranetTarget() {
        return isTrustAllCertificatesEnabled();
    }

    @Override
    public boolean hasUntrustedCertificateWhenInternetTarget() {
        return isTrustAllCertificatesEnabled();
    }

    public static boolean isPDSScriptTrustingAllCertificates(Map<String, String> parametersToSend) {
        String useSecHubStorage = parametersToSend.get(PDSDefaultParameterKeyConstants.PARAM_KEY_PDS_CONFIG_SCRIPT_TRUSTALL_CERTIFICATES_ENABLED);
        return Boolean.parseBoolean(useSecHubStorage);
    }

}
