package com.sequenceiq.redbeams.service;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.common.mappable.CloudPlatform;

@Service
public class UserGeneratorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserGeneratorService.class);

    private static final int MAX_RANDOM_INT_FOR_CHARACTER = 26;

    private static final int USER_NAME_LENGTH = 10;

    public String generateUserName() {
        return ThreadLocalRandom.current().ints(0, MAX_RANDOM_INT_FOR_CHARACTER)
                .limit(USER_NAME_LENGTH).boxed()
                .map(i -> Character.toString((char) ('a' + i)))
                .collect(Collectors.joining());
    }

    /**
     * Updates an original username based on additional information available
     * after allocation. Updates depend on the cloud platform, and since some
     * platforms do not require updates, this method may return the original
     * username without updates.
     *
     * @param  originalUserName original username (generated by this service, nominally)
     * @param  cloudPlatform    cloud provider whose rules must be followed, if known
     * @param  dbHostname       database server hostname
     * @return                  updated username
     */
    public String updateUserName(String originalUserName, Optional<CloudPlatform> cloudPlatform, String dbHostname) {
        if (!cloudPlatform.isPresent()) {
            return originalUserName;
        }

        String updatedUserName;
        switch (cloudPlatform.get()) {
            case AZURE:
                String dbShortHostname = dbHostname.split("\\.")[0];
                updatedUserName = originalUserName + "@" + dbShortHostname;
                LOGGER.debug("Updating username for Azure to {}", updatedUserName);
                break;
            default:
                updatedUserName = originalUserName;
                break;
        }

        return updatedUserName;
    }
}
