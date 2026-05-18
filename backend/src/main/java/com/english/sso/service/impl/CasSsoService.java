package com.english.sso.service.impl;

import com.english.exception.BusinessException;
import com.english.sso.config.SsoProperties;
import com.english.sso.dto.SsoUserInfo;
import com.english.sso.service.SsoProtocolType;
import com.english.sso.service.SsoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * CAS 2.0 SSO 服务实现
 *
 * @author LangHack Team
 * @since 2026-04-09
 */
@Slf4j
@Service
@Profile("sso")
@ConditionalOnProperty(name = "sso.protocol", havingValue = "CAS")
public class CasSsoService implements SsoService {

    @Autowired
    private SsoProperties ssoProperties;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public String generateLoginUrl(String serviceUrl) {
        if (!isEnabled()) {
            throw new BusinessException("CAS SSO 未启用");
        }

        String casServerUrl = ssoProperties.getCas().getServerUrl();
        String loginUrl = casServerUrl + "/login?service=" +
                URLEncoder.encode(serviceUrl, StandardCharsets.UTF_8);

        log.info("生成 CAS 登录 URL: {}", loginUrl);
        return loginUrl;
    }

    @Override
    public SsoUserInfo validateCallback(Map<String, String> callbackParams, String callbackUrl) {
        String ticket = callbackParams.get("ticket");
        if (ticket == null || ticket.isEmpty()) {
            throw new BusinessException("缺少 CAS ticket 参数");
        }

        log.info("验证 CAS ticket: {}, service URL: {}", ticket, callbackUrl);

        // 调用 CAS /serviceValidate 验证 ticket
        String casServerUrl = ssoProperties.getCas().getServerUrl();
        String validateUrl = casServerUrl + "/serviceValidate?service=" +
                URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8) +
                "&ticket=" + URLEncoder.encode(ticket, StandardCharsets.UTF_8);

        log.info("CAS 验证请求 URL: {}", validateUrl);

        try {
            // 必须用 URI.create() 而非直接传 String！
            // RestTemplate.getForObject(String url, ...) 会把已编码的 %3A%2F 再编码成 %253A%252F（双重编码）
            // 导致 CAS 收到的 service 参数与 ticket 绑定的原始 URL 不匹配 → INVALID_SERVICE
            // URI.create() 告诉 RestTemplate 这个 URL 已经完成编码，不要再处理
            String response = restTemplate.getForObject(URI.create(validateUrl), String.class);
            // 完整 XML 响应可能含用户属性（memberOf/containerId 等 PII），仅在 debug 级别记录
            if (log.isDebugEnabled()) {
                log.debug("CAS 验证响应: {}", response);
            }

            return parseCasResponse(response);
        } catch (BusinessException e) {
            // parseCasResponse 抛出的业务异常（如 INVALID_SERVICE/INVALID_TICKET）应透传，不再包裹
            throw e;
        } catch (HttpStatusCodeException e) {
            // CAS 服务器返回了 HTTP 错误（4xx/5xx），记录状态码 + 响应体片段便于排查
            // 典型场景：404（serverUrl 配置错误）、500（CAS 服务异常）、403（网络策略阻断）
            log.error("CAS ticket 验证失败（HTTP 错误） validateUrl={} status={} bodyPreview={} err={}",
                    validateUrl, e.getStatusCode(), truncateForLog(e.getResponseBodyAsString()),
                    e.getMessage(), e);
            throw new BusinessException("CAS 认证失败（服务器错误 " + e.getStatusCode() + "），请联系管理员");
        } catch (ResourceAccessException e) {
            // 网络层错误：连接超时 / DNS 失败 / SSL 握手失败
            log.error("CAS ticket 验证失败（网络/超时） validateUrl={} err={}",
                    validateUrl, e.getMessage(), e);
            throw new BusinessException("CAS 服务网络异常，请稍后重试");
        } catch (RestClientException e) {
            // 其他 RestTemplate 异常（如反序列化失败）
            log.error("CAS ticket 验证失败（HTTP 客户端异常） validateUrl={} err={}",
                    validateUrl, e.getMessage(), e);
            throw new BusinessException("CAS 认证失败，请稍后重试");
        } catch (Exception e) {
            // 兜底：记录上下文 + 堆栈
            log.error("CAS ticket 验证失败（未预期异常） validateUrl={} ticket={} err={}",
                    validateUrl, ticket, e.getMessage(), e);
            throw new BusinessException("CAS 认证失败，请稍后重试");
        }
    }

    @Override
    public SsoProtocolType getProtocolType() {
        return SsoProtocolType.CAS;
    }

    @Override
    public boolean isEnabled() {
        return ssoProperties.isEnabled() && ssoProperties.getCas().isEnabled();
    }

    @Override
    public String generateLogoutUrl(String serviceUrl) {
        String casServerUrl = ssoProperties.getCas().getServerUrl();
        // CAS logout 协议：service 参数指定登出后跳转回的应用地址
        return casServerUrl + "/logout?service=" +
                URLEncoder.encode(serviceUrl, StandardCharsets.UTF_8);
    }

    /**
     * 解析 CAS XML 响应
     */
    private SsoUserInfo parseCasResponse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // XXE 防护：禁用 DTD / 外部实体，避免恶意 XML 导致 SSRF / 本地文件读取 / DoS
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            // 检查是否认证成功
            NodeList successNodes = doc.getElementsByTagName("cas:authenticationSuccess");
            if (successNodes.getLength() == 0) {
                // 认证失败
                NodeList failureNodes = doc.getElementsByTagName("cas:authenticationFailure");
                if (failureNodes.getLength() > 0) {
                    String failureMessage = failureNodes.item(0).getTextContent();
                    throw new BusinessException("CAS 认证失败: " + failureMessage);
                }
                throw new BusinessException("CAS 响应格式错误");
            }

            Element successElement = (Element) successNodes.item(0);

            // 提取用户名
            NodeList userNodes = successElement.getElementsByTagName("cas:user");
            if (userNodes.getLength() == 0) {
                throw new BusinessException("CAS 响应中缺少用户名");
            }
            String casSubject = userNodes.item(0).getTextContent();

            // 提取属性（参照官方 Cas20ServiceTicketValidator.extractCustomAttributes()）：
            // 同一属性名出现多次时（如多个 memberOf），单值存 String，多值存 List<String>
            Map<String, Object> attributes = new HashMap<>();
            NodeList attributesNodes = successElement.getElementsByTagName("cas:attributes");
            if (attributesNodes.getLength() > 0) {
                Element attributesElement = (Element) attributesNodes.item(0);
                NodeList children = attributesElement.getChildNodes();

                // 先收集同名属性的所有值
                Map<String, List<String>> attrValuesMap = new LinkedHashMap<>();
                for (int i = 0; i < children.getLength(); i++) {
                    if (children.item(i) instanceof Element) {
                        Element attrElement = (Element) children.item(i);
                        String attrName = attrElement.getLocalName();
                        String attrValue = attrElement.getTextContent();
                        attrValuesMap.computeIfAbsent(attrName, k -> new ArrayList<>()).add(attrValue);
                    }
                }

                // 单值存 String，多值存 List<String>（与官方 cas-client-core 行为一致）
                for (Map.Entry<String, List<String>> entry : attrValuesMap.entrySet()) {
                    List<String> values = entry.getValue();
                    if (values.size() == 1) {
                        attributes.put(entry.getKey(), values.get(0));
                    } else {
                        attributes.put(entry.getKey(), values);
                    }
                }
            }

            // 构建 SsoUserInfo
            SsoUserInfo userInfo = new SsoUserInfo();
            userInfo.setProvider("CAS");
            userInfo.setSsoUserId(casSubject);
            userInfo.setSsoUsername(casSubject);
            userInfo.setAttributes(attributes);

            // 映射属性
            Map<String, String> attrMapping = ssoProperties.getCas().getAttributes();
            if (attrMapping.containsKey("username")) {
                String usernameKey = attrMapping.get("username");
                String mappedUsername = firstAttributeValue(attributes.get(usernameKey));
                if (mappedUsername != null) {
                    userInfo.setSsoUsername(mappedUsername);
                }
            }
            if (attrMapping.containsKey("real-name")) {
                String realNameKey = attrMapping.get("real-name");
                userInfo.setRealName(firstAttributeValue(attributes.get(realNameKey)));
            }
            if (attrMapping.containsKey("email")) {
                String emailKey = attrMapping.get("email");
                userInfo.setEmail(firstAttributeValue(attributes.get(emailKey)));
            }
            if (attrMapping.containsKey("member-of")) {
                String memberOfKey = attrMapping.get("member-of");
                Object memberOfValue = attributes.get(memberOfKey);
                userInfo.setMemberOf(parseMemberOf(memberOfValue));
            }

            // 回退：CAS 常不返回 memberOf，但可能返回 containerId（如 ou=2000001,ou=People）
            // containerId 包含 OU 信息，可用于角色映射
            if ((userInfo.getMemberOf() == null || userInfo.getMemberOf().isEmpty())
                    && attributes.containsKey("containerId")) {
                Object containerIdValue = attributes.get("containerId");
                List<String> containerIdAsMemberOf = parseMemberOf(containerIdValue);
                if (!containerIdAsMemberOf.isEmpty()) {
                    log.info("CAS 未返回 memberOf，回退使用 containerId 作为组织信息: {}", containerIdAsMemberOf);
                    userInfo.setMemberOf(containerIdAsMemberOf);
                }
            }

            log.info("CAS 用户信息解析成功: subject={}, username={}, realName={}",
                    casSubject, userInfo.getSsoUsername(), userInfo.getRealName());
            return userInfo;

        } catch (BusinessException e) {
            // CAS 认证失败 / 响应格式错误等业务异常透传（已含可读消息）
            throw e;
        } catch (Exception e) {
            // XML 解析 / 属性提取异常：记录 XML 片段便于诊断上游响应格式变化
            log.error("解析 CAS 响应失败 xmlPreview={} err={}",
                    truncateForLog(xml), e.getMessage(), e);
            throw new BusinessException("解析 CAS 响应失败，请联系管理员");
        }
    }

    /**
     * 截断响应体片段用于日志，避免：
     *   ① CAS XML 响应过长污染日志；
     *   ② 若响应包含用户敏感属性（memberOf/containerId），限制回显长度。
     */
    private String truncateForLog(String body) {
        if (body == null) return "null";
        return body.length() > 500 ? body.substring(0, 500) + "...(truncated)" : body;
    }

    /**
     * 解析 memberOf 属性（可能是字符串或数组）
     */
    private List<String> parseMemberOf(Object memberOfValue) {
        if (memberOfValue == null) {
            return new ArrayList<>();
        }

        if (memberOfValue instanceof String) {
            // memberOf 是单个字符串，直接返回单元素列表
            // 注意：不要按逗号分割，因为 LDAP DN 本身包含逗号
            return Collections.singletonList((String) memberOfValue);
        } else if (memberOfValue instanceof List) {
            return (List<String>) memberOfValue;
        }

        return new ArrayList<>();
    }

    private String firstAttributeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            String str = ((String) value).trim();
            return str.isEmpty() ? null : str;
        }
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item == null) {
                    continue;
                }
                String str = String.valueOf(item).trim();
                if (!str.isEmpty()) {
                    return str;
                }
            }
        }
        return null;
    }
}
