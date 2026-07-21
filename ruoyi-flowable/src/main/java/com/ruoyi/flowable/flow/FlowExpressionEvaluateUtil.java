package com.ruoyi.flowable.flow;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorBoolean;
import com.googlecode.aviator.runtime.type.AviatorObject;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流程条件表达式计算工具。
 * <p>
 * 在保留 Aviator 原有比较、逻辑运算能力的基础上，兼容 Flowable/JUEL 常见的
 * Java 风格 {@code variable.contains(value)} 写法。
 * </p>
 */
public final class FlowExpressionEvaluateUtil {

    private static final String CONTAINS_FUNCTION_NAME = "__flow_contains";

    /**
     * 匹配简单变量或属性路径上的 contains 调用，例如：
     * companyEntity.contains("主体")、form.companyEntity.contains("主体")。
     */
    private static final Pattern CONTAINS_METHOD_PATTERN = Pattern.compile(
            "([\\p{L}_$][\\p{L}\\p{N}_$]*(?:\\.[\\p{L}_$][\\p{L}\\p{N}_$]*)*)\\s*\\.contains\\s*\\(");

    private static final AviatorEvaluatorInstance EVALUATOR = AviatorEvaluator.newInstance();

    static {
        EVALUATOR.addFunction(new ContainsFunction());
    }

    private FlowExpressionEvaluateUtil() {
    }

    /**
     * 计算条件表达式。
     *
     * @param variables  表达式变量，允许为 null
     * @param expression 表达式，支持 {@code ${...}}、{@code #{...}} 或不带包裹符的形式
     * @return 仅表达式结果为 true 时返回 true；空表达式、缺失变量及计算异常均返回 false
     */
    public static boolean evaluate(Map<String, Object> variables, String expression) {
        if (expression == null || expression.isEmpty()) {
            return false;
        }
        try {
            String aviatorExpression = rewriteContainsMethod(unwrap(expression.trim()));
            Expression compiledExpression = EVALUATOR.compile(aviatorExpression, true);
            Object result = compiledExpression.execute(
                    variables == null ? Collections.emptyMap() : variables);
            return result instanceof Boolean ? (Boolean) result : Boolean.parseBoolean(String.valueOf(result));
        } catch (Exception ignored) {
            // 预演条件计算失败时按不命中处理，与 PredictNodeUtil 原有行为保持一致。
            return false;
        }
    }

    private static String unwrap(String expression) {
        if (expression.length() >= 3
                && (expression.startsWith("${") || expression.startsWith("#{"))
                && expression.endsWith("}")) {
            return expression.substring(2, expression.length() - 1).trim();
        }
        return expression;
    }

    /**
     * 只改写字符串字面量之外的 contains 调用，避免误处理引号中的普通文本。
     */
    private static String rewriteContainsMethod(String expression) {
        StringBuilder result = new StringBuilder(expression.length() + 16);
        StringBuilder plainSegment = new StringBuilder();
        char quote = 0;
        boolean escaped = false;

        for (int i = 0; i < expression.length(); i++) {
            char current = expression.charAt(i);
            if (quote == 0) {
                if (current == '\'' || current == '"') {
                    appendRewrittenSegment(result, plainSegment);
                    result.append(current);
                    quote = current;
                } else {
                    plainSegment.append(current);
                }
            } else {
                result.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
            }
        }
        appendRewrittenSegment(result, plainSegment);
        return result.toString();
    }

    private static void appendRewrittenSegment(StringBuilder result, StringBuilder segment) {
        if (segment.toString().isEmpty()) {
            return;
        }
        Matcher matcher = CONTAINS_METHOD_PATTERN.matcher(segment);
        result.append(matcher.replaceAll(CONTAINS_FUNCTION_NAME + "($1, "));
        segment.setLength(0);
    }

    /**
     * contains 的空值安全实现：字符串按子串判断，集合按元素判断，Map 按 key 判断。
     */
    private static final class ContainsFunction extends AbstractFunction {

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject target, AviatorObject expected) {
            Object targetValue = target.getValue(env);
            Object expectedValue = expected.getValue(env);
            boolean contains = false;
            if (targetValue instanceof CharSequence && expectedValue != null) {
                contains = targetValue.toString().contains(expectedValue.toString());
            } else if (targetValue instanceof Collection<?>) {
                contains = ((Collection<?>) targetValue).contains(expectedValue);
            } else if (targetValue instanceof Map<?, ?>) {
                contains = ((Map<?, ?>) targetValue).containsKey(expectedValue);
            }
            return AviatorBoolean.valueOf(contains);
        }

        @Override
        public String getName() {
            return CONTAINS_FUNCTION_NAME;
        }
    }
}
