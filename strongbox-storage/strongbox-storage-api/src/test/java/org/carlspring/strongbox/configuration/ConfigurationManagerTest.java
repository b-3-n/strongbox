package org.carlspring.strongbox.configuration;

import org.carlspring.strongbox.StorageApiTestConfig;
import org.carlspring.strongbox.booters.PropertiesBooter;
import org.carlspring.strongbox.data.CacheManagerTestExecutionListener;
import org.carlspring.strongbox.storage.StorageDto;
import org.carlspring.strongbox.storage.repository.RepositoryDto;
import org.carlspring.strongbox.storage.routing.MutableRoutingRule;
import org.carlspring.strongbox.storage.routing.MutableRoutingRuleRepository;
import org.carlspring.strongbox.storage.routing.MutableRoutingRules;
import org.carlspring.strongbox.storage.routing.RoutingRuleTypeEnum;
import org.carlspring.strongbox.yaml.YAMLMapperFactory;
import org.carlspring.strongbox.yaml.repository.CustomRepositoryConfigurationDto;
import org.carlspring.strongbox.yaml.repository.remote.RemoteRepositoryConfigurationDto;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.collect.Sets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author mtodorov
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@ContextConfiguration(classes = StorageApiTestConfig.class)
@TestExecutionListeners(listeners = { CacheManagerTestExecutionListener.class },
                        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class ConfigurationManagerTest
{

    private static final String TEST_CLASSES = "target/test-classes";

    private static final String CONFIGURATION_BASEDIR = TEST_CLASSES + "/yaml";

    private static final String CONFIGURATION_OUTPUT_FILE = CONFIGURATION_BASEDIR + "/strongbox-saved-cm.yaml";

    private static final String STORAGE0 = "storage0";

    @Inject
    private YAMLMapperFactory yamlMapperFactory;

    @Inject
    private ConfigurationManager configurationManager;

    @Inject
    private PropertiesBooter propertiesBooter;

    private YAMLMapper yamlMapper;


    @BeforeEach
    public void setUp()
            throws IOException
    {
        Path yamlPath = Paths.get(CONFIGURATION_BASEDIR);
        if (Files.notExists(yamlPath))
        {
            Files.createDirectories(yamlPath);
        }

        yamlMapper = yamlMapperFactory.create(
                Sets.newHashSet(CustomRepositoryConfigurationDto.class, RemoteRepositoryConfigurationDto.class));
    }

    @Test
    public void testParseConfiguration()
    {
        final Configuration configuration = configurationManager.getConfiguration();

        assertNotNull(configuration);
        assertNotNull(configuration.getStorages());
        assertNotNull(configuration.getRoutingRules());
        // assertFalse(configuration.getRoutingRules().getWildcardAcceptedRules().getRoutingRules().isEmpty());
        // assertFalse(configuration.getRoutingRules().getWildcardDeniedRules().getRoutingRules().isEmpty());

        for (String storageId : configuration.getStorages().keySet())
        {
            assertNotNull(storageId, "Storage ID was null!");
            // assertTrue(!configuration.getStorages().get(storageId).getRepositories().isEmpty(), "No repositories were parsed!");
        }

        assertTrue(configuration.getStorages().size() > 0, "Unexpected number of storages!");
        assertNotNull(configuration.getVersion(), "Incorrect version!");
        assertEquals(48080, configuration.getPort(), "Incorrect port number!");
        assertTrue(configuration.getStorages()
                                .get(STORAGE0)
                                .getRepositories()
                                .get("snapshots")
                                .isSecured(),
                   "Repository should have required authentication!");

        assertTrue(configuration.getStorages()
                                .get(STORAGE0)
                                .getRepositories()
                                .get("releases")
                                .allowsDirectoryBrowsing());
    }

    @Test
    public void testStoreConfiguration()
            throws IOException
    {
        MutableProxyConfiguration proxyConfigurationGlobal = new MutableProxyConfiguration();
        proxyConfigurationGlobal.setUsername("maven");
        proxyConfigurationGlobal.setPassword("password");
        proxyConfigurationGlobal.setHost("192.168.100.1");
        proxyConfigurationGlobal.setPort(8080);
        proxyConfigurationGlobal.addNonProxyHost("192.168.100.1");
        proxyConfigurationGlobal.addNonProxyHost("192.168.100.2");

        MutableProxyConfiguration proxyConfigurationRepository1 = new MutableProxyConfiguration();
        proxyConfigurationRepository1.setUsername("maven");
        proxyConfigurationRepository1.setPassword("password");
        proxyConfigurationRepository1.setHost("192.168.100.5");
        proxyConfigurationRepository1.setPort(8080);
        proxyConfigurationRepository1.addNonProxyHost("192.168.100.10");
        proxyConfigurationRepository1.addNonProxyHost("192.168.100.11");

        RepositoryDto repository1 = new RepositoryDto("snapshots");
        repository1.setProxyConfiguration(proxyConfigurationRepository1);

        RepositoryDto repository2 = new RepositoryDto("releases");

        StorageDto storage = new StorageDto();
        storage.setId("myStorageId");
        storage.setBasedir(new File(propertiesBooter.getVaultDirectory() + "/storages" + STORAGE0)
                                   .getAbsolutePath());
        storage.addRepository(repository1);
        storage.addRepository(repository2);

        MutableConfiguration configuration = new MutableConfiguration();
        configuration.addStorage(storage);
        configuration.setProxyConfiguration(proxyConfigurationGlobal);

        File outputFile = new File(CONFIGURATION_OUTPUT_FILE);
        yamlMapper.writeValue(outputFile, configuration);

        assertTrue(outputFile.length() > 0, "Failed to store the produced YAML!");
    }

    @Test
    public void testGroupRepositories()
            throws IOException
    {
        RepositoryDto repository1 = new RepositoryDto("snapshots");
        RepositoryDto repository2 = new RepositoryDto("ext-snapshots");
        RepositoryDto repository3 = new RepositoryDto("grp-snapshots");
        repository3.addRepositoryToGroup(repository1.getId());
        repository3.addRepositoryToGroup(repository2.getId());

        StorageDto storage = new StorageDto(STORAGE0);
        storage.setBasedir(new File(propertiesBooter.getVaultDirectory() + "/storages" + STORAGE0).getAbsolutePath());
        storage.addRepository(repository1);
        storage.addRepository(repository2);
        storage.addRepository(repository3);

        MutableConfiguration configuration = new MutableConfiguration();
        configuration.addStorage(storage);

        File outputFile = new File(CONFIGURATION_OUTPUT_FILE);

        yamlMapper.writeValue(outputFile, configuration);

        assertTrue(outputFile.length() > 0, "Failed to store the produced YAML!");

        MutableConfiguration c = yamlMapper.readValue(outputFile.toURI().toURL(), MutableConfiguration.class);

        assertEquals(2,
                     c.getStorages().get(STORAGE0)
                      .getRepositories()
                      .get("grp-snapshots")
                      .getGroupRepositories()
                      .size(),
                     "Failed to read repository groups!");
    }

    @Test
    public void testRoutingRules()
            throws IOException
    {

        MutableRoutingRule routingRule = MutableRoutingRule.create(STORAGE0,
                                                                   "group-internal",
                                                                   Arrays.asList(
                                                                           new MutableRoutingRuleRepository(STORAGE0,
                                                                                                            "int-releases"),
                                                                           new MutableRoutingRuleRepository(STORAGE0,
                                                                                                            "int-snapshots")),
                                                                   ".*(com|org)/artifacts.denied.in.memory.*",
                                                                   RoutingRuleTypeEnum.ACCEPT);
        MutableRoutingRules routingRules = new MutableRoutingRules();
        routingRules.setRules(Collections.singletonList(routingRule));

        try (OutputStream os = new ByteArrayOutputStream())
        {
            yamlMapper.writeValue(os, routingRules);
        }

        // parser.store(routingRules, System.out);

        // Assuming that if there is no error, there is no problem.
        // Not optimal, but that's as good as it gets right now.
    }

    @Test
    public void testCorsConfiguration()
            throws IOException
    {

        MutableCorsConfiguration corsConfiguration = new MutableCorsConfiguration(
                Arrays.asList("http://example.com", "https://github.com/strongbox", "http://carlspring.org"));

        MutableConfiguration configuration = new MutableConfiguration();
        configuration.setCorsConfiguration(corsConfiguration);

        File outputFile = new File(CONFIGURATION_OUTPUT_FILE);

        yamlMapper.writeValue(outputFile, configuration);

        assertTrue(outputFile.length() > 0, "Failed to store the produced YAML!");

        MutableConfiguration c = yamlMapper.readValue(outputFile, MutableConfiguration.class);

        assertEquals(3,
                     c.getCorsConfiguration().getAllowedOrigins().size(),
                     "Failed to read saved cors allowedOrigins!");
    }

    @Test
    public void testSmtpConfiguration()
            throws IOException
    {

        final String smtpHost = "localhost";
        final Integer smtpPort = 25;
        final String smtpConnection = "tls";
        final String smtpUsername = "user-name";
        final String smtpPassword = "user-password";

        MutableSmtpConfiguration smtpConfiguration = new MutableSmtpConfiguration(smtpHost,
                                                                                  smtpPort,
                                                                                  smtpConnection,
                                                                                  smtpUsername,
                                                                                  smtpPassword);

        MutableConfiguration configuration = new MutableConfiguration();
        configuration.setSmtpConfiguration(smtpConfiguration);

        File outputFile = new File(CONFIGURATION_OUTPUT_FILE);

        yamlMapper.writeValue(outputFile, configuration);

        assertTrue(outputFile.length() > 0, "Failed to store the produced YAML!");

        MutableConfiguration c = yamlMapper.readValue(outputFile, MutableConfiguration.class);

        MutableSmtpConfiguration savedSmtpConfiguration = c.getSmtpConfiguration();

        assertEquals(smtpHost, savedSmtpConfiguration.getHost(), "Failed to read saved smtp host!");
        assertEquals(smtpPort, savedSmtpConfiguration.getPort(), "Failed to read saved smtp port!");
        assertEquals(smtpConnection, savedSmtpConfiguration.getConnection(), "Failed to read saved smtp connection!");
        assertEquals(smtpUsername, savedSmtpConfiguration.getUsername(), "Failed to read saved smtp username!");
        assertEquals(smtpPassword, savedSmtpConfiguration.getPassword(), "Failed to read saved smtp password!");
    }
}
