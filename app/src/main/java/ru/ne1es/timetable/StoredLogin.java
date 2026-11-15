package ru.ne1es.timetable;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Android Keystore holds the AES key. Only authenticated ciphertext is written to disk. */
final class StoredLogin {
 private static final String ALIAS="ulstu-login-v1";
 private static AtomicFile file(Context context,String name){
  return new AtomicFile(new File(context.getNoBackupFilesDir(),name));
 }
 private static KeyStore keyStore()throws Exception{
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");
  store.load(null);
  return store;
 }
 static synchronized void save(Context context,String login,String password)throws Exception{
  save(context,ALIAS,"ulstu-login.enc",new JSONObject().put("login",login).put("password",password));
 }
 private static void save(Context context,String alias,String name,JSONObject data)throws Exception{
  SecretKey key=(SecretKey)keyStore().getKey(alias,null);
  if(key==null){
   KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
   generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
   key=generator.generateKey();
  }
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(Cipher.ENCRYPT_MODE,key);
  byte[] plain=data.toString().getBytes(StandardCharsets.UTF_8);
  AtomicFile target=file(context,name);
  FileOutputStream stream=null;
  try{
   byte[] encrypted=cipher.doFinal(plain),iv=cipher.getIV();
   if(iv.length!=12)throw new IllegalStateException("Неподдерживаемый формат ключа");
   stream=target.startWrite();
   stream.write(iv);
   stream.write(encrypted);
   target.finishWrite(stream);
  }catch(Exception error){
   if(stream!=null)target.failWrite(stream);
   throw error;
  }finally{Arrays.fill(plain,(byte)0);}
 }
 static synchronized JSONObject read(Context context)throws Exception{
  return read(context,ALIAS,"ulstu-login.enc");
 }
 static synchronized void saveCookies(Context context,JSONObject data)throws Exception{
  save(context,"ulstu-cookies-v1","ulstu-cookies.enc",data);
 }
 static synchronized JSONObject readCookies(Context context)throws Exception{
  return read(context,"ulstu-cookies-v1","ulstu-cookies.enc");
 }
 private static JSONObject read(Context context,String alias,String name)throws Exception{
  AtomicFile target=file(context,name);
  if(!target.getBaseFile().exists())return null;
  byte[] encrypted=target.readFully();
  SecretKey key=(SecretKey)keyStore().getKey(alias,null);
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,encrypted,0,12));
  byte[] plain=cipher.doFinal(encrypted,12,encrypted.length-12);
  try{return new JSONObject(new String(plain,StandardCharsets.UTF_8));}
  finally{Arrays.fill(plain,(byte)0);}
 }
 static synchronized void clear(Context context)throws Exception{
  file(context,"ulstu-login.enc").delete();
  file(context,"ulstu-cookies.enc").delete();
  keyStore().deleteEntry("ulstu-cookies-v1");
  keyStore().deleteEntry(ALIAS);
 }
}
