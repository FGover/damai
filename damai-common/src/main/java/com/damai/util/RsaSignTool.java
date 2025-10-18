package com.damai.util;

import cn.hutool.crypto.asymmetric.SignAlgorithm;
import com.alibaba.fastjson.JSON;
import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: rsa签名工具
 * @author: 阿星不是程序员
 **/
@Slf4j
public class RsaSignTool {

    // 签名算法类型
    private final static String SIGN_TYPE = "RSA";
    // 编码格式
    private final static String CHARSET = "utf-8";

    /**
     * 使用私钥对参数进行RSA-SHA256签名
     *
     * @param params     参数Map
     * @param privateKey 私钥（Base64编码）
     * @return 签名字符串（Base64编码）
     */
    public static String rsaSign256(Map<String, String> params, String privateKey) {
        // 将参数map拼接为字符串
        String content = buildParam(params);
        return rsaSign256(content, privateKey);
    }

    /**
     * 使用私钥对原始字符串进行RSA-SHA256签名
     *
     * @param content    待签名内容
     * @param privateKey 私钥（Base64编码）
     * @return 签名字符串（Base64编码）
     */
    public static String rsaSign256(String content, String privateKey) {
        try {
            // 将私钥从Base64编码转换为字节数组
            byte[] keyBytes = Base64.getDecoder().decode(privateKey);
            // 使用PKCS8EncodedKeySpec封装私钥字节
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            // 创建KeyFactory实例
            KeyFactory keyFactory = KeyFactory.getInstance(SIGN_TYPE);
            // 使用SHA256withRSA算法获取签名对象
            Signature si = Signature.getInstance(SignAlgorithm.SHA256withRSA.getValue());
            // 初始化签名对象（加载私钥）
            si.initSign(keyFactory.generatePrivate(keySpec));
            // 设置需要签名的原文数据
            si.update(content.getBytes(CHARSET));
            // 生成签名（得到字节数组）
            byte[] sign = si.sign();
            // 对签名字节进行Base64编码返回
            return Base64.getEncoder().encodeToString(sign);
        } catch (Exception e) {
            log.error("sign256 error", e);
            throw new DaMaiFrameException(BaseCode.GENERATE_RSA_SIGN_ERROR);
        }
    }

    /**
     * 将参数 Map 按字典序拼接为 key1=value1&key2=value2 形式
     *
     * @param params 参数集合
     * @return 拼接后的字符串
     */
    private static String buildParam(Map<String, String> params) {
        // 获取所有参数名
        List<String> keys = new ArrayList<>(params.keySet());
        // 将参数名按字典序排序
        Collections.sort(keys);
        // 创建字符串拼接器
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size() - 1; i++) {
            String key = keys.get(i);
            String value = params.get(key);
            // 拼接 key=value
            sb.append(buildKeyValue(key, value, false));
            sb.append("&");
        }
        // 拼接最后一个参数（不加&）
        String tailKey = keys.get(keys.size() - 1);
        String tailValue = params.get(tailKey);
        sb.append(buildKeyValue(tailKey, tailValue, false));
        return sb.toString();
    }

    /**
     * 验证参数 Map 中的签名是否正确（RSA-SHA256）
     *
     * @param params    参数集合（包含 sign）
     * @param publicKey 公钥（Base64编码）
     * @return true 表示验证通过
     */
    public static boolean verifyRsaSign256(Map<String, String> params, String publicKey) {
        try {
            // 提取签名值
            String sign = params.get("sign");
            // 获取参与验签的原文（去掉 sign 和 files）
            String content = getSignCheckContent(params);
            // 调用验签方法
            return verifyRsaSign256(content.getBytes(CHARSET), sign, publicKey);
        } catch (Exception e) {
            log.error("verifyRsaSign256 error", e);
            throw new DaMaiFrameException(BaseCode.RSA_SIGN_ERROR);
        }
    }

    /**
     * 核心验签方法：对比签名与内容
     *
     * @param dataBytes 原文字节数组
     * @param sign      签名（Base64编码）
     * @param publicKey 公钥（Base64编码）
     * @return true 表示验证通过
     */
    public static boolean verifyRsaSign256(byte[] dataBytes, String sign, String publicKey) throws
            NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        // 解码签名字节
        byte[] signByte = Base64.getDecoder().decode(sign);
        // 解码公钥字节
        byte[] encodedKey = Base64.getDecoder().decode(publicKey);
        // 使用SHA256withRSA算法获取签名对象
        Signature signature = Signature.getInstance(SignAlgorithm.SHA256withRSA.getValue());
        // 获取密钥工厂
        KeyFactory keyFac = KeyFactory.getInstance(SIGN_TYPE);
        // 生成公钥对象
        PublicKey puk = keyFac.generatePublic(new X509EncodedKeySpec(encodedKey));
        // 初始化签名对象（加载公钥）
        signature.initVerify(puk);
        // 设置需要签名的原文数据
        signature.update(dataBytes);
        // 验证签名
        return signature.verify(signByte);

    }

    /**
     * 拼接键值对 key=value
     *
     * @param key      键
     * @param value    值
     * @param isEncode 是否进行 URL 编码
     * @return key=value 串
     */
    private static String buildKeyValue(String key, String value, boolean isEncode) {
        StringBuilder sb = new StringBuilder();
        sb.append(key);
        sb.append("=");
        if (isEncode) {
            try {
                // 如果需要则进行URL编码
                sb.append(URLEncoder.encode(value, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                sb.append(value);
            }
        } else {
            sb.append(value);
        }
        return sb.toString();
    }

    /**
     * 获取用于验签的内容（去掉 sign 和 files）
     *
     * @param params 参数 Map
     * @return 拼接好的待验签字符串
     */
    private static String getSignCheckContent(Map<String, String> params) {
        if (params == null) {
            return null;
        }
        params.remove("sign");  // 去掉 sign 字段
        params.remove("files");  // 去掉 files 字段

        return buildParam(params);
    }

    /**
     * rsa签名私钥
     */
    public static String signPrivateKey = "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCht3FKchtYAS4Gcjb2bEOfAXA+vcVSZjTV3JaS7sqOSFbDGDV2o1nLuPktdr5ByjUtA+SOBJuzEKm3qWueVq8jGAl9FiLie0TiWnHrre95zPQRe1PE8LvqQnk81mFAYv3KZWyTFb6Ky4O6CWnr2zGI1QM5JNA+Jm/AhwDxWXjN1yVzNhWi3fdBPl3WajI/hI+WWwqs9Vm3GI0e3IfN1cDxyecjoWbJKIZ1quzRm+FcifcjQaDF72+QHtnik1BaNKzbmvVmJGypvi9X450wPnUJNvhOl/t0CUfbdSeihseo2WOf+Z9p5NQUuRdKFgISRT6yxd9746liqXkpxm4NMIahAgMBAAECggEAFtoiUz/Op1/7TgPjymzAHX8JioQslxlETBhQ2tCNpQ+J2yXXoD0zGju4UnleJ1PYsdTD/mGeUu5+3So+v/BF7XKfHKL9KP38XPQk9wXsOk0BDFteGg1esJrWIQe2VG/opyov7pT7CQf7RFXCNwcRd+GKBBA0sSOjVRR+yJw5GvUbm6Ytf/EkCwLNXrsEFhlcXyE5N29TARrHau9JA1DkjX10O1f3Q80p4q4IaNXm7NIWJ8M4TAhN8KTYyTNZruXD5vHlv/NPTdCaCIAS863Mg46qkXXJ3wrjsCKy48GWAKWQiiMHf9p7F9cUPLQZdjuyxmfrJwWeiLzoFo63tDbpcQKBgQDQHtcKnYIB6vTkf8FOXl2wwd0INkZjr2YVJG8MJzB4HdRwuyYYHGbzR4Xk9ag+kg5En3nykA+cMxn+54eQmQo1aMX8En/bla6UJ8AvBVLP068JzR9KdQ49AQqq/74aLEFr0lL8ectXRCJz1pohZ8Gs7DtuMio/4AXJqL9gisolVQKBgQDG66qPH2GK6Hdn4ZTv5qPw7CpogKzZGpVKg4Q8Bztxnj+p22MoiMDdZ89O0mgEytecPSRro7ziyhp1zoJzULru9NdxTPqx1OZW9nj46elYfZAqbo3NJByNjDQrBZDtxwUfz4rkpegliZ/ljRJjWh/Oa8UyGSJwfnsVRaDnx2EcHQKBgFFKGnhc+TDCkxDFDb4Mgc/OiQTyHiBFnDvZ1T4L+JSSIi4+Cy0Tuup/Hz9E7Ig0CDqph7pEprQ+CYNU79B81k3yNJK2rxYXqu7Xb+ttyuC+L/pGEljEy+DsDTypU5lpe8wfhKZ09AWL6WERi3ZMzos6YiQyl+oHGHuh285bp4VZAoGAF3WZotFvnoM1+dFX0EciFHq1sadjOyNwcd46zR2JPCgOmAiglBo0rKfeggw8ajxF2042ql8gGpr9LeGR7umZci777YfHlQtnst/Uen6Tn3UHeImbPZNBrsvXJy+73N740ryWQ8rxKuQlMFxHy+HIGH8LPZJLRnsUJvkUNeGEqV0CgYAr9K/w9ruTVKs5/Q2dGMhZLVadsKMo6b4rMzuO5/iH94q2XgzHKw+to4zUJUyCiwxW9w2rM4xSZu20zfBl4Lt4VNllHbLAWU0onPa24ZBiHv9IZKBtzPpyYdi6Y5/D6DfNNTQO6B/XvyzCikhtBFlZSVj23rLqhFD9N/gWP/kMaA==";
    /**
     * rsa签名公钥
     */
    public static String signPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAobdxSnIbWAEuBnI29mxDnwFwPr3FUmY01dyWku7KjkhWwxg1dqNZy7j5LXa+Qco1LQPkjgSbsxCpt6lrnlavIxgJfRYi4ntE4lpx663vecz0EXtTxPC76kJ5PNZhQGL9ymVskxW+isuDuglp69sxiNUDOSTQPiZvwIcA8Vl4zdclczYVot33QT5d1moyP4SPllsKrPVZtxiNHtyHzdXA8cnnI6FmySiGdars0ZvhXIn3I0Ggxe9vkB7Z4pNQWjSs25r1ZiRsqb4vV+OdMD51CTb4Tpf7dAlH23UnoobHqNljn/mfaeTUFLkXShYCEkU+ssXfe+OpYql5KcZuDTCGoQIDAQAB";

    /**
     * rsa数据加密私钥
     */
    public static String dataPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAirLDI4SPxLXAjk+CMJWrdREnQjJJQgEd7RAw+ZCPZKBFfkoPa5YjcYQzqtc4RPOszBZhPmGr732WLA0O2U0WFnPG6vva9x7pYQot4u5IoncRl7kBb89d1XdR5DZxKovQyDM91CkLikq8h0sBVTkfX2Jz34LmYd8TPQ4BSHUDE5h+f42WkUYG9PCaXvPg+yv4+1AwJeXI/wW181h1JQ5cmogFXIHEFOxS/wwtnoijwmRv/3nKhdyYZbpC2V7F2xq9jWuTBL01Oj3sRhbykHDW2aK2oJ53U5vqlaC6XsheCabMqeqjDPCa8rUjp10pWy7LneYxVigVuONOmlvt56ja7QIDAQAB";

    /**
     * rsa数据解密私钥
     */
    public static String dataPrivateKey = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCKssMjhI/EtcCOT4Iwlat1ESdCMklCAR3tEDD5kI9koEV+Sg9rliNxhDOq1zhE86zMFmE+YavvfZYsDQ7ZTRYWc8bq+9r3HulhCi3i7kiidxGXuQFvz13Vd1HkNnEqi9DIMz3UKQuKSryHSwFVOR9fYnPfguZh3xM9DgFIdQMTmH5/jZaRRgb08Jpe8+D7K/j7UDAl5cj/BbXzWHUlDlyaiAVcgcQU7FL/DC2eiKPCZG//ecqF3JhlukLZXsXbGr2Na5MEvTU6PexGFvKQcNbZoragnndTm+qVoLpeyF4Jpsyp6qMM8JrytSOnXSlbLsud5jFWKBW4406aW+3nqNrtAgMBAAECggEAbCHOTSSOSZhBlTGbmHE3iT9kUhGOV60zPZ0/8XGouZTSWRE4UHJvE5M0DN9Z+TfY4gwYqF/RghdxOsq7ZuLYc4yz6oOMRNmOrZ8YAzIu4qrdxmHwItGSoFg0Oi3PsJHspgh9DakqXBjEPt5VHbI5KU5CdGFDZ85Y22LN0UWYrm8wOj6P4qJU/bXIOYYl4LfQRIdF6z/0a/ooi0JvQWfgiVMjymTaeF/aRNxqt2Mm09hWEfKUzYX96LINrCJ0DG/Rz+xyShW3rajOHxdY61gyIPybQcXehtacGW+DE/4M6KrWZYUoH/X3KUaXjq0Ed+Sj2cSmv30idcdyDsRmSK7VaQKBgQDOTVoMVhrzDAZf1DywMwSyObQJ4tFr1MwNLXD1wqRJta07448IuRGOdXoq4hoEw1gBAcHyWlIT/8hY7fiBvMi0YETL2gN+PWLhJL24dxR92ACsKP1j3cYM6b0HmfyrbTUuzk+dWcI835ADzWq+b093+EUI/7VZUAALowk2o/DtQwKBgQCsHEgxK7cW6nRLevssnnBIWQ5IlJlUsp/tqyn+IKwuUyJzJWRXvxag4UdMUcMOB/syw9XFyqBgEc+cY1jgoMVsacFyeAkUjfaxGro83H/Inx2Ge25zH/OxZkmL7kkz+bApKhmtHva8mCGu4ZHh0B6PFoffZV6idiAokgVmir/8DwKBgQCrDv5cfkUIRG9ApFXR7+uz8B61l8n39FFhl80zKjpZF/hVUUF3hSTmj8hFqIbUbjkZVKDBWFz4Uj2IZ4GH6cYtsik5MkN1OGc1seZR/wMRuboNBkvcs7YVXPYtSGR2rC3N6qmfGh7xpJngXUJmNxuYqVZsuMJhFPGEtKHeGZ+aywKBgHEPsyz59rCLHBJpm47YFhKwzf1IAOHu5biPdGqItBNKcZsKuTwbP5Y350pve59ABvh2RXxFe80gZi3p5XzKoGZzoqy7xdtG1wPI9wb8IsV8IT0y4H+oQcIL28ycoGIQaHTiPzPG33dMyPPFIrwgp7J/ropGYUCAMOf15K5T/4JpAoGAHwLE5jaJxK5VKKe9x4uPWcPLMgJY3s9J2dn1ZrZTOKqE0d9GCbSEhZNtGOrAzAmWdy3GC0rmP0Fs2DIyTJg9iPsn/ISt0PYvIzSJ6CQeAuEtsjdEdKiVa/um10XeD4dT4vAyMHJpg9WV2NR/vjiuk2YIM1CXo/r/7Gp6aiHY+Bc=";


    public static void main(String[] args) {
        //v1加密版本
        parameterTransferV1();
        //v2加密版本
        //parameterTransferV2();
    }

    public static void parameterTransferV1() {
        Map<String, String> map = new HashMap<>(8);
        //基础参数
        map.put("code", "0001");
        //业务参数
        map.put("businessBody", "{\"id\":\"1111\",\"sleepTime\":10}");
        //签名
        String sign = RsaSignTool.rsaSign256(map, signPrivateKey);
        System.out.println("签名:" + sign);
        map.put("sign", sign);
        //验签
        boolean result = RsaSignTool.verifyRsaSign256(map, signPublicKey);
        System.out.println("签名结果:" + result);
    }

    public static void parameterTransferV2() {
        Map<String, String> map = new HashMap<>(8);
        //基础参数
        map.put("code", "0001");

        //参数加密后再签名
        Map<String, Object> businessMap = new HashMap<>(8);
        businessMap.put("id", "1111");
        businessMap.put("sleepTime", 10);

        //将业务参数进行加密
        String encrypt = RsaTool.encrypt(JSON.toJSONString(businessMap), dataPublicKey);
        System.out.println("参数加密后:" + encrypt);

        String decrypt = RsaTool.decrypt(encrypt, dataPrivateKey);
        System.out.println("参数解密后:" + decrypt);

        //将未加密的业务参数和基础参数进行拼接
        map.put("businessBody", JSON.toJSONString(businessMap));
        //rsa生成签名
        String sign = RsaSignTool.rsaSign256(map, signPrivateKey);
        System.out.println("签名:" + sign);
        map.put("sign", sign);
        //rsa进行验签
        boolean result = RsaSignTool.verifyRsaSign256(map, signPublicKey);
        System.out.println("签名结果:" + result);
    }
}
