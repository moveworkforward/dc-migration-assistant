package com.atlassian.migration.datacenter.core.aws.auth;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import lombok.SneakyThrows;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Class for managing the storage and retrieval of AWS Credentials. Should not be used for direct access to credentials
 * except for in a CredentialsProvider implementation. This class stores credentials encrypted with the server id as the
 * key and uses password based encryption.
 */
@Component
@Primary
public class EncryptedCredentialsStorage implements ReadCredentialsService, WriteCredentialsService {

    private static final String AWS_CREDS_PLUGIN_STORAGE_KEY = "com.atlassian.migration.datacenter.core.aws.auth";
    private static final String ACCESS_KEY_ID_PLUGIN_STORAGE_SUFFIX = ".accessKeyId";
    private static final String SECRET_ACCESS_KEY_PLUGIN_STORAGE_SUFFIX = ".secretAccessKey";
    private static final String ENCRYPTION_KEY_FILE_NAME = "keyFile";
    private static final String ENCRYPTION_SALT_FILE_NAME = "saltFile";
    private static final Logger LOGGER = Logger.getLogger(EncryptedCredentialsStorage.class);

    private final TextEncryptor textEncryptor;
    private final PluginSettings pluginSettings;

    @Autowired
    public EncryptedCredentialsStorage(@ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettings = pluginSettingsFactory.createGlobalSettings();
        String password = getPassword();
        String salt = getSalt();
        this.textEncryptor = Encryptors.text(password, salt);
    }

    @SneakyThrows
    private static String getPassword() {
        File passwordFile = new File(ENCRYPTION_KEY_FILE_NAME);
        if (passwordFile.exists()) {
            StringBuilder keyBuilder = new StringBuilder();
            try (Stream<String> stream = Files.lines(Paths.get(passwordFile.getPath()), StandardCharsets.UTF_8)) {
                stream.forEach(keyBuilder::append);
            } catch (Exception e) {
                e.printStackTrace();
            }
            String encoded = keyBuilder.toString();
            return encoded;
        } else {
            String keyString = KeyGenerators.string().generateKey();
            Path p = passwordFile.toPath();
            Files.write(p, keyString.getBytes(StandardCharsets.UTF_8));
            passwordFile.setWritable(false, true);
            return keyString;
        }
    }

    private static String getSalt() {
        File saltFile = new File(ENCRYPTION_SALT_FILE_NAME);
        if (saltFile.exists()) {
            StringBuilder saltBuilder = new StringBuilder();
            try (Stream<String> stream = Files.lines(Paths.get(saltFile.getPath()), StandardCharsets.UTF_8)) {
                stream.forEach(saltBuilder::append);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return saltBuilder.toString();
        } else {
            String key = KeyGenerators.string().generateKey();
            try (FileOutputStream outputStream = new FileOutputStream(saltFile)) {
                outputStream.write(key.getBytes());
            } catch (IOException ex) {
                LOGGER.error(ex.getLocalizedMessage());
            }
            saltFile.setWritable(false, true);
            return key;
        }
    }

    @Override
    public String getAccessKeyId() {
        String raw = (String) this.pluginSettings.get(AWS_CREDS_PLUGIN_STORAGE_KEY + ACCESS_KEY_ID_PLUGIN_STORAGE_SUFFIX);
        return this.decryptString(raw);
    }

    public void setAccessKeyId(String accessKeyId) {
        this.pluginSettings.put(AWS_CREDS_PLUGIN_STORAGE_KEY + ACCESS_KEY_ID_PLUGIN_STORAGE_SUFFIX, this.encryptString(accessKeyId));
    }

    @Override
    public void storeAccessKeyId(String accessKeyId) {
        this.setAccessKeyId(accessKeyId);
    }

    @Override
    public void storeSecretAccessKey(String secretAccessKey) {
        this.setSecretAccessKey(secretAccessKey);
    }

    @Override
    public String getSecretAccessKey() {
        String raw = (String) this.pluginSettings.get(AWS_CREDS_PLUGIN_STORAGE_KEY + SECRET_ACCESS_KEY_PLUGIN_STORAGE_SUFFIX);
        return this.decryptString(raw);
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.pluginSettings.put(AWS_CREDS_PLUGIN_STORAGE_KEY + SECRET_ACCESS_KEY_PLUGIN_STORAGE_SUFFIX, this.encryptString(secretAccessKey));
    }

    /**
     * The string encryption function
     *
     * @param raw the string to be encrypted
     * @return the encrypted string
     */
    private String encryptString(final String raw) {
        try {
            return this.textEncryptor.encrypt(raw);
        } catch (Exception e) {
            LOGGER.error(e.getLocalizedMessage());
            return null;
        }
    }

    /**
     * The string decryption function
     *
     * @param encrypted string to be decrypted
     * @return the decrypted plaintext string
     */
    private String decryptString(final String encrypted) {
        try {
            return this.textEncryptor.decrypt(encrypted);
        } catch (Exception e) {
            LOGGER.error(e.getLocalizedMessage());
            return null;
        }
    }


}
