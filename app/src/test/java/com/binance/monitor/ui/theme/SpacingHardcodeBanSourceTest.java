/*
 * 主链 spacing hardcode 禁入测试：
 * 1) 这不是全仓库扫描，只锁主链 XML（res/layout*）与主链 Java（ui 包）。
 * 2) Java 侧升级为轻量源码解析：先做语句/作用域扫描，再做表达式级解析。
 * 3) 允许 0；仅真正线宽语义允许 1dp；setLineSpacing/setLineHeight 单独严控。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SpacingHardcodeBanSourceTest {

    private static final double EPSILON = 1e-6;
    private static final int MAX_RESOLVE_DEPTH = 8;
    private static final int MAX_RESOLVED_VALUE_COUNT = 16;

    private static final Pattern XML_DIMENSION_LITERAL = Pattern.compile(
            "((?:android|app|tools):[\\w.:-]+)\\s*=\\s*\"(-?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+))(dp|sp)\""
    );

    private static final Pattern JAVA_SPACING_METHOD_CALL = Pattern.compile(
            "(?s)\\b(setPadding(?:Relative)?|setMargins?|setMarginStart|setMarginEnd|"
                    + "setContentInsets(?:Relative)?|setCompoundDrawablePadding|setDividerPadding|"
                    + "setHorizontalSpacing|setVerticalSpacing|setItemSpacing|setLineSpacing|setLineHeight)"
                    + "\\s*\\((.*)\\)\\s*;"
    );

    private static final Pattern JAVA_SPACING_FIELD_ASSIGNMENT = Pattern.compile(
            "(?s)\\b(leftMargin|rightMargin|topMargin|bottomMargin|marginStart|marginEnd|"
                    + "contentInsetStart|contentInsetEnd|contentInsetLeft|contentInsetRight|dividerPadding|"
                    + "lineSpacingExtra|lineHeight)\\s*=\\s*(?![=])(.*)\\s*;"
    );

    private static final Pattern JAVA_DECLARATION_PATTERN = Pattern.compile(
            "(?s)^\\s*(?:public|protected|private)?\\s*(?:(?:static|final|volatile|transient|synchronized)\\s+)*"
                    + "[\\w<>\\[\\].?,\\s$]+?\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.+?)\\s*;\\s*$"
    );

    private static final Pattern NUMBER_LITERAL_PATTERN = Pattern.compile(
            "^[-+]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)[fFdD]?$"
    );

    private static final Pattern SIMPLE_IDENTIFIER_PATTERN = Pattern.compile(
            "^(?:this\\.)?(?:[A-Za-z_][A-Za-z0-9_]*\\.)*([A-Za-z_][A-Za-z0-9_]*)$"
    );

    private static final List<String> XML_SPACING_TOKENS = Arrays.asList(
            "padding",
            "layout_margin",
            "margin",
            "drawablepadding",
            "compounddrawablepadding",
            "contentinset",
            "spacing",
            "gap",
            "gutter",
            "reserve",
            "offset",
            "linespacing",
            "lineheight"
    );

    private static final List<String> XML_NON_SPACING_TOKENS = Arrays.asList(
            "letterspacing"
    );

    private static final List<String> DP_HELPER_NAMES = Arrays.asList(
            "dp",
            "dpToPx",
            "pxFromDp"
    );

    private static final Set<String> ALLOWED_XML_FILES = Collections.emptySet();
    private static final Set<String> ALLOWED_JAVA_FILES = Collections.emptySet();

    private static final List<String> MAIN_ROOT_CANDIDATES = Arrays.asList(
            "src/main",
            "app/src/main"
    );

    private static final String MAIN_XML_DIR_PREFIX = "layout";
    private static final String MAIN_JAVA_UI_PACKAGE = "java/com/binance/monitor/ui/";

    @Test
    public void mainChainXmlShouldNotKeepNonZeroSpacingLiteral() throws Exception {
        Path mainRoot = resolveMainSourceRoot();
        List<String> violations = new ArrayList<>();
        for (Path file : collectMainChainXmlFiles(mainRoot.resolve("res"))) {
            String relativePath = toMainRelativePath(mainRoot, file);
            if (ALLOWED_XML_FILES.contains(relativePath)) {
                continue;
            }
            String source = readUtf8(file);
            Matcher matcher = XML_DIMENSION_LITERAL.matcher(source);
            while (matcher.find()) {
                String attribute = matcher.group(1);
                if (!isXmlSpacingAttribute(attribute)) {
                    continue;
                }
                double value = parseNumeric(matcher.group(2));
                if (isAllowedSpacingLiteral(value, isLineWidthSemantic(attribute, source, matcher.start()))) {
                    continue;
                }
                violations.add(relativePath + " -> " + attribute + "=" + matcher.group(2) + matcher.group(3));
            }
        }
        assertTrue(buildViolationMessage("主链 XML", violations), violations.isEmpty());
    }

    @Test
    public void mainChainRuntimeJavaShouldNotKeepLiteralSpacingCalls() throws Exception {
        Path mainRoot = resolveMainSourceRoot();
        List<String> violations = new ArrayList<>();
        for (Path file : collectFiles(mainRoot.resolve("java"), ".java")) {
            if (!isMainChainJavaFile(mainRoot, file)) {
                continue;
            }
            String relativePath = toMainRelativePath(mainRoot, file);
            if (ALLOWED_JAVA_FILES.contains(relativePath)) {
                continue;
            }
            violations.addAll(collectJavaViolationsFromSource(relativePath, readUtf8(file)));
        }
        assertTrue(buildViolationMessage("主链 Java", violations), violations.isEmpty());
    }

    @Test
    public void expressionResolverShouldCatchWrappedAliasExpressions() {
        String source = ""
                + "class Demo {\n"
                + "  void bind(View view, Context context, LayoutParams params) {\n"
                + "    int top = (int) ((float) 8);\n"
                + "    int gap = (int) (dp(context, 6f));\n"
                + "    view.setPadding(0, top, 0, 0);\n"
                + "    params.topMargin = gap;\n"
                + "  }\n"
                + "}\n";

        List<String> violations = collectJavaViolationsFromSource(
                "src/main/java/com/binance/monitor/ui/demo/Demo.java",
                source
        );

        assertTrue(buildViolationMessage("wrapped alias", violations), violations.size() >= 2);
    }

    @Test
    public void expressionResolverShouldCatchConditionalDpExpressions() {
        String source = ""
                + "class Demo {\n"
                + "  void bind(View view, Context context, boolean compact) {\n"
                + "    view.setPadding(0, (int) dp(context, compact ? 2f : 4f), 0, 0);\n"
                + "  }\n"
                + "}\n";

        List<String> violations = collectJavaViolationsFromSource(
                "src/main/java/com/binance/monitor/ui/demo/Demo.java",
                source
        );

        assertTrue(buildViolationMessage("conditional dp", violations), !violations.isEmpty());
    }

    // 以源码作用域为基础收集 Java 违规项。
    private static List<String> collectJavaViolationsFromSource(String relativePath, String source) {
        SourceStructure structure = buildSourceStructure(source);
        List<String> violations = new ArrayList<>();
        for (StatementInfo statement : structure.statements) {
            collectMethodCallViolations(statement, structure, relativePath, violations);
            collectFieldAssignmentViolations(statement, structure, relativePath, violations);
        }
        return violations;
    }

    // 收集方法调用上的 spacing 违规。
    private static void collectMethodCallViolations(
            StatementInfo statement,
            SourceStructure structure,
            String relativePath,
            List<String> violations
    ) {
        Matcher matcher = JAVA_SPACING_METHOD_CALL.matcher(statement.text);
        if (!matcher.find()) {
            return;
        }
        String methodName = matcher.group(1);
        String argumentsText = matcher.group(2);
        if ("setLineSpacing".equals(methodName)) {
            collectLineSpacingViolations(statement, structure, relativePath, argumentsText, violations);
            return;
        }
        List<String> arguments = splitTopLevelArguments(argumentsText);
        boolean lineWidthSemantic = isLineWidthSemantic(methodName, structure.source, statement.start);
        for (String argument : arguments) {
            Set<Double> values = resolveNumericValues(argument, statement.scope, structure, statement.start, new LinkedHashSet<>(), 0);
            Set<Double> illegalValues = collectIllegalValues(values, lineWidthSemantic);
            if (illegalValues.isEmpty()) {
                continue;
            }
            violations.add(relativePath + " -> "
                    + compactExpression(argument) + "=" + formatNumericValues(illegalValues)
                    + " @ " + compactStatement(statement.text));
        }
    }

    // 收集字段赋值上的 spacing 违规。
    private static void collectFieldAssignmentViolations(
            StatementInfo statement,
            SourceStructure structure,
            String relativePath,
            List<String> violations
    ) {
        Matcher matcher = JAVA_SPACING_FIELD_ASSIGNMENT.matcher(statement.text);
        if (!matcher.find()) {
            return;
        }
        String fieldName = matcher.group(1);
        String expression = matcher.group(2);
        if ("lineSpacingExtra".equals(fieldName)) {
            collectLineSpacingValueViolation(
                    relativePath,
                    statement,
                    structure,
                    expression,
                    0d,
                    "lineSpacingExtra",
                    violations
            );
            return;
        }
        Set<Double> values = resolveNumericValues(expression, statement.scope, structure, statement.start, new LinkedHashSet<>(), 0);
        Set<Double> illegalValues = collectIllegalValues(values, false);
        if (!illegalValues.isEmpty()) {
            violations.add(relativePath + " -> "
                    + fieldName + "=" + formatNumericValues(illegalValues)
                    + " @ " + compactStatement(statement.text));
        }
    }

    // setLineSpacing(extra, multiplier) 分别按 0 和 1 校验。
    private static void collectLineSpacingViolations(
            StatementInfo statement,
            SourceStructure structure,
            String relativePath,
            String argumentsText,
            List<String> violations
    ) {
        List<String> arguments = splitTopLevelArguments(argumentsText);
        if (arguments.size() < 2) {
            violations.add(relativePath + " -> setLineSpacing 参数不足 @ " + compactStatement(statement.text));
            return;
        }
        collectLineSpacingValueViolation(
                relativePath,
                statement,
                structure,
                arguments.get(0),
                0d,
                "lineSpacingExtra",
                violations
        );
        collectLineSpacingValueViolation(
                relativePath,
                statement,
                structure,
                arguments.get(1),
                1d,
                "lineSpacingMultiplier",
                violations
        );
    }

    // 校验 lineSpacing 的单个数值表达式。
    private static void collectLineSpacingValueViolation(
            String relativePath,
            StatementInfo statement,
            SourceStructure structure,
            String expression,
            double allowedValue,
            String label,
            List<String> violations
    ) {
        Set<Double> values = resolveNumericValues(expression, statement.scope, structure, statement.start, new LinkedHashSet<>(), 0);
        Set<Double> illegalValues = collectIllegalValuesForExactValue(values, allowedValue);
        if (!illegalValues.isEmpty()) {
            violations.add(relativePath + " -> "
                    + label + "=" + formatNumericValues(illegalValues)
                    + " @ " + compactStatement(statement.text));
        }
    }

    // 构建源码语句与作用域结构，避免单纯按正则做全文件盲扫。
    private static SourceStructure buildSourceStructure(String source) {
        ScopeInfo rootScope = new ScopeInfo(null, 0, 0);
        List<StatementInfo> statements = new ArrayList<>();
        Deque<ScopeInfo> scopeStack = new ArrayDeque<>();
        scopeStack.push(rootScope);

        int statementStart = 0;
        int parenthesisDepth = 0;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        char stringDelimiter = 0;

        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (current == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inString || inChar) {
                if (current == '\\') {
                    i++;
                    continue;
                }
                if (current == stringDelimiter) {
                    inString = false;
                    inChar = false;
                }
                continue;
            }
            if (current == '/' && next == '/') {
                inLineComment = true;
                i++;
                continue;
            }
            if (current == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (current == '"' || current == '\'') {
                stringDelimiter = current;
                inString = current == '"';
                inChar = current == '\'';
                continue;
            }
            if (current == '(') {
                parenthesisDepth++;
                continue;
            }
            if (current == ')') {
                if (parenthesisDepth > 0) {
                    parenthesisDepth--;
                }
                continue;
            }
            if (current == '{') {
                ScopeInfo childScope = new ScopeInfo(scopeStack.peek(), scopeStack.peek().depth + 1, i);
                scopeStack.peek().children.add(childScope);
                scopeStack.push(childScope);
                statementStart = i + 1;
                continue;
            }
            if (current == '}') {
                scopeStack.peek().end = i;
                scopeStack.pop();
                statementStart = i + 1;
                continue;
            }
            if (current == ';' && parenthesisDepth == 0) {
                String statementText = source.substring(statementStart, i + 1);
                StatementInfo statement = new StatementInfo(statementText, statementStart, i + 1, scopeStack.peek());
                statements.add(statement);
                DeclarationInfo declaration = tryParseDeclaration(statement, scopeStack.peek());
                if (declaration != null) {
                    scopeStack.peek().declarations.add(declaration);
                }
                statementStart = i + 1;
            }
        }
        rootScope.end = source.length();
        return new SourceStructure(source, rootScope, statements);
    }

    // 尝试解析变量声明语句。
    private static DeclarationInfo tryParseDeclaration(StatementInfo statement, ScopeInfo scope) {
        Matcher matcher = JAVA_DECLARATION_PATTERN.matcher(statement.text);
        if (!matcher.matches()) {
            return null;
        }
        return new DeclarationInfo(
                matcher.group(1),
                matcher.group(2),
                statement.start,
                scope
        );
    }

    // 解析表达式中能静态确定出的数值集合。
    private static Set<Double> resolveNumericValues(
            String expression,
            ScopeInfo scope,
            SourceStructure structure,
            int statementOffset,
            Set<String> visitingNames,
            int depth
    ) {
        if (expression == null || depth > MAX_RESOLVE_DEPTH) {
            return Collections.emptySet();
        }

        String normalized = stripHarmlessWrappers(expression);
        if (normalized.isEmpty()) {
            return Collections.emptySet();
        }

        if (isNumberLiteral(normalized)) {
            return singletonValue(parseNumeric(normalized));
        }

        String helperName = extractWholeMethodName(normalized);
        if (helperName != null && DP_HELPER_NAMES.contains(helperName)) {
            List<String> arguments = splitTopLevelArguments(extractWholeMethodArguments(normalized));
            if (arguments.isEmpty()) {
                return Collections.emptySet();
            }
            return resolveNumericValues(
                    arguments.get(arguments.size() - 1),
                    scope,
                    structure,
                    statementOffset,
                    visitingNames,
                    depth + 1
            );
        }

        int conditionalIndex = findTopLevelQuestionMark(normalized);
        if (conditionalIndex >= 0) {
            int colonIndex = findMatchingConditionalColon(normalized, conditionalIndex);
            if (colonIndex > conditionalIndex) {
                Set<Double> values = new LinkedHashSet<>();
                values.addAll(resolveNumericValues(
                        normalized.substring(conditionalIndex + 1, colonIndex),
                        scope,
                        structure,
                        statementOffset,
                        visitingNames,
                        depth + 1
                ));
                values.addAll(resolveNumericValues(
                        normalized.substring(colonIndex + 1),
                        scope,
                        structure,
                        statementOffset,
                        visitingNames,
                        depth + 1
                ));
                return limitAndNormalize(values);
            }
        }

        BinarySplit additiveSplit = splitByTopLevelOperator(normalized, '+', '-');
        if (additiveSplit != null) {
            Set<Double> leftValues = resolveNumericValues(
                    additiveSplit.left,
                    scope,
                    structure,
                    statementOffset,
                    visitingNames,
                    depth + 1
            );
            Set<Double> rightValues = resolveNumericValues(
                    additiveSplit.right,
                    scope,
                    structure,
                    statementOffset,
                    visitingNames,
                    depth + 1
            );
            return combineBinaryValues(leftValues, rightValues, additiveSplit.operator);
        }

        BinarySplit multiplicativeSplit = splitByTopLevelOperator(normalized, '*', '/', '%');
        if (multiplicativeSplit != null) {
            Set<Double> leftValues = resolveNumericValues(
                    multiplicativeSplit.left,
                    scope,
                    structure,
                    statementOffset,
                    visitingNames,
                    depth + 1
            );
            Set<Double> rightValues = resolveNumericValues(
                    multiplicativeSplit.right,
                    scope,
                    structure,
                    statementOffset,
                    visitingNames,
                    depth + 1
            );
            return combineBinaryValues(leftValues, rightValues, multiplicativeSplit.operator);
        }

        if (normalized.startsWith("+")) {
            return resolveNumericValues(
                    normalized.substring(1),
                    scope,
                    structure,
                    statementOffset,
                    visitingNames,
                    depth + 1
            );
        }
        if (normalized.startsWith("-")) {
            Set<Double> innerValues = resolveNumericValues(
                    normalized.substring(1),
                    scope,
                    structure,
                    statementOffset,
                    visitingNames,
                    depth + 1
            );
            Set<Double> negated = new LinkedHashSet<>();
            for (double value : innerValues) {
                negated.add(normalizeValue(-value));
            }
            return limitAndNormalize(negated);
        }

        String simpleName = extractSimpleIdentifier(normalized);
        if (simpleName != null && visitingNames.add(simpleName)) {
            try {
                DeclarationInfo declaration = findVisibleDeclaration(simpleName, scope, statementOffset);
                if (declaration == null) {
                    return Collections.emptySet();
                }
                return resolveNumericValues(
                        declaration.expression,
                        declaration.scope,
                        structure,
                        declaration.offset,
                        visitingNames,
                        depth + 1
                );
            } finally {
                visitingNames.remove(simpleName);
            }
        }

        return Collections.emptySet();
    }

    // 在当前作用域链上查找可见声明；类字段允许不受定义顺序限制。
    private static DeclarationInfo findVisibleDeclaration(String name, ScopeInfo scope, int offset) {
        ScopeInfo current = scope;
        while (current != null) {
            DeclarationInfo declaration = current.findLocalDeclaration(name, offset);
            if (declaration != null) {
                return declaration;
            }
            if (current.depth == 1) {
                DeclarationInfo classDeclaration = current.findClassDeclaration(name);
                if (classDeclaration != null) {
                    return classDeclaration;
                }
            }
            current = current.parent;
        }
        return null;
    }

    // 去掉外围括号和连续 cast。
    private static String stripHarmlessWrappers(String expression) {
        String normalized = expression == null ? "" : expression.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            String withoutOuter = stripOuterParentheses(normalized);
            if (!withoutOuter.equals(normalized)) {
                normalized = withoutOuter.trim();
                changed = true;
            }

            CastSplit castSplit = tryStripLeadingCast(normalized);
            if (castSplit != null) {
                normalized = castSplit.expression.trim();
                changed = true;
            }
        }
        return normalized;
    }

    // 去掉覆盖整个表达式的外围括号。
    private static String stripOuterParentheses(String expression) {
        String normalized = expression.trim();
        while (normalized.startsWith("(") && normalized.endsWith(")")) {
            int closing = findMatchingRightParenthesis(normalized, 0);
            if (closing != normalized.length() - 1) {
                return normalized;
            }
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }

    // 尝试识别最前面的 cast 包裹。
    private static CastSplit tryStripLeadingCast(String expression) {
        String normalized = expression.trim();
        if (!normalized.startsWith("(")) {
            return null;
        }
        int closing = findMatchingRightParenthesis(normalized, 0);
        if (closing <= 0 || closing >= normalized.length() - 1) {
            return null;
        }
        String maybeType = normalized.substring(1, closing).trim();
        if (!looksLikeCastType(maybeType)) {
            return null;
        }
        return new CastSplit(maybeType, normalized.substring(closing + 1));
    }

    // 粗判 cast 类型文本。
    private static boolean looksLikeCastType(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (Character.isLetterOrDigit(current) || current == '_' || current == '.'
                    || current == '$' || current == '[' || current == ']' || current == '?'
                    || current == '<' || current == '>' || Character.isWhitespace(current)) {
                continue;
            }
            return false;
        }
        return true;
    }

    // 拆分整个表达式级别的方法名。
    private static String extractWholeMethodName(String expression) {
        int parenIndex = expression.indexOf('(');
        if (parenIndex <= 0 || !expression.endsWith(")")) {
            return null;
        }
        int closing = findMatchingRightParenthesis(expression, parenIndex);
        if (closing != expression.length() - 1) {
            return null;
        }
        return extractSimpleIdentifier(expression.substring(0, parenIndex));
    }

    // 提取整个表达式级别的方法参数文本。
    private static String extractWholeMethodArguments(String expression) {
        int parenIndex = expression.indexOf('(');
        return expression.substring(parenIndex + 1, expression.length() - 1);
    }

    // 判断是否是纯数字字面量。
    private static boolean isNumberLiteral(String expression) {
        return NUMBER_LITERAL_PATTERN.matcher(expression.trim()).matches();
    }

    // 提取 simple identifier / member-select 尾部名称。
    private static String extractSimpleIdentifier(String expression) {
        Matcher matcher = SIMPLE_IDENTIFIER_PATTERN.matcher(expression.trim());
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1);
    }

    // 在顶层找到条件表达式问号。
    private static int findTopLevelQuestionMark(String expression) {
        int depth = 0;
        for (int i = 0; i < expression.length(); i++) {
            char current = expression.charAt(i);
            if (current == '(') {
                depth++;
                continue;
            }
            if (current == ')') {
                if (depth > 0) {
                    depth--;
                }
                continue;
            }
            if (current == '?' && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    // 为顶层条件表达式找到配对冒号。
    private static int findMatchingConditionalColon(String expression, int questionIndex) {
        int depth = 0;
        int nestedConditionalDepth = 0;
        for (int i = questionIndex + 1; i < expression.length(); i++) {
            char current = expression.charAt(i);
            if (current == '(') {
                depth++;
                continue;
            }
            if (current == ')') {
                if (depth > 0) {
                    depth--;
                }
                continue;
            }
            if (depth != 0) {
                continue;
            }
            if (current == '?') {
                nestedConditionalDepth++;
                continue;
            }
            if (current == ':') {
                if (nestedConditionalDepth == 0) {
                    return i;
                }
                nestedConditionalDepth--;
            }
        }
        return -1;
    }

    // 按顶层二元运算符切分表达式。
    private static BinarySplit splitByTopLevelOperator(String expression, char... operators) {
        Set<Character> operatorSet = new LinkedHashSet<>();
        for (char operator : operators) {
            operatorSet.add(operator);
        }
        int depth = 0;
        for (int i = expression.length() - 1; i >= 0; i--) {
            char current = expression.charAt(i);
            if (current == ')') {
                depth++;
                continue;
            }
            if (current == '(') {
                if (depth > 0) {
                    depth--;
                }
                continue;
            }
            if (depth != 0 || !operatorSet.contains(current)) {
                continue;
            }
            if (isUnaryOperatorPosition(expression, i)) {
                continue;
            }
            return new BinarySplit(
                    expression.substring(0, i),
                    expression.substring(i + 1),
                    current
            );
        }
        return null;
    }

    // 判断当前 +/- 是否是一元运算位置。
    private static boolean isUnaryOperatorPosition(String expression, int operatorIndex) {
        char operator = expression.charAt(operatorIndex);
        if (operator != '+' && operator != '-') {
            return false;
        }
        for (int i = operatorIndex - 1; i >= 0; i--) {
            char previous = expression.charAt(i);
            if (Character.isWhitespace(previous)) {
                continue;
            }
            return previous == '(' || previous == ',' || previous == '?' || previous == ':'
                    || previous == '+' || previous == '-' || previous == '*'
                    || previous == '/' || previous == '%';
        }
        return true;
    }

    // 合并二元运算两侧的静态数值。
    private static Set<Double> combineBinaryValues(Set<Double> leftValues, Set<Double> rightValues, char operator) {
        if (leftValues.isEmpty() || rightValues.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Double> resolved = new LinkedHashSet<>();
        for (double left : leftValues) {
            for (double right : rightValues) {
                Double value = applyBinaryOperator(left, right, operator);
                if (value == null) {
                    return Collections.emptySet();
                }
                resolved.add(normalizeValue(value));
                if (resolved.size() > MAX_RESOLVED_VALUE_COUNT) {
                    return Collections.emptySet();
                }
            }
        }
        return limitAndNormalize(resolved);
    }

    // 计算单个二元运算结果。
    private static Double applyBinaryOperator(double left, double right, char operator) {
        switch (operator) {
            case '+':
                return left + right;
            case '-':
                return left - right;
            case '*':
                return left * right;
            case '/':
                if (Math.abs(right) < EPSILON) {
                    return null;
                }
                return left / right;
            case '%':
                if (Math.abs(right) < EPSILON) {
                    return null;
                }
                return left % right;
            default:
                return null;
        }
    }

    // 拆分顶层方法参数，兼容括号嵌套。
    private static List<String> splitTopLevelArguments(String expression) {
        String normalized = expression == null ? "" : expression.trim();
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> arguments = new ArrayList<>();
        int depth = 0;
        int tokenStart = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (current == '(') {
                depth++;
                continue;
            }
            if (current == ')') {
                if (depth > 0) {
                    depth--;
                }
                continue;
            }
            if (current == ',' && depth == 0) {
                arguments.add(normalized.substring(tokenStart, i).trim());
                tokenStart = i + 1;
            }
        }
        arguments.add(normalized.substring(tokenStart).trim());
        return arguments;
    }

    // 找到与起始左括号配对的右括号。
    private static int findMatchingRightParenthesis(String expression, int leftIndex) {
        int depth = 0;
        for (int i = leftIndex; i < expression.length(); i++) {
            char current = expression.charAt(i);
            if (current == '(') {
                depth++;
                continue;
            }
            if (current == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    // 判断 XML 属性是否属于 spacing 语义。
    private static boolean isXmlSpacingAttribute(String attributeName) {
        String lower = attributeName.toLowerCase(Locale.ROOT);
        for (String token : XML_NON_SPACING_TOKENS) {
            if (lower.contains(token)) {
                return false;
            }
        }
        for (String token : XML_SPACING_TOKENS) {
            if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    // 收集不满足放行规则的数值。
    private static Set<Double> collectIllegalValues(Set<Double> values, boolean lineWidthSemantic) {
        Set<Double> illegalValues = new LinkedHashSet<>();
        for (double value : values) {
            if (!isAllowedSpacingLiteral(value, lineWidthSemantic)) {
                illegalValues.add(normalizeValue(value));
            }
        }
        return illegalValues;
    }

    // 收集不满足固定允许值的数值。
    private static Set<Double> collectIllegalValuesForExactValue(Set<Double> values, double allowedValue) {
        Set<Double> illegalValues = new LinkedHashSet<>();
        for (double value : values) {
            if (Math.abs(value - allowedValue) >= EPSILON) {
                illegalValues.add(normalizeValue(value));
            }
        }
        return illegalValues;
    }

    // 放行规则：0 值允许；1dp 仅在线宽语义允许。
    private static boolean isAllowedSpacingLiteral(double value, boolean lineWidthSemantic) {
        if (Math.abs(value) < EPSILON) {
            return true;
        }
        return lineWidthSemantic && Math.abs(value - 1d) < EPSILON;
    }

    // 判断当前位置是否是线宽/描边语义。
    private static boolean isLineWidthSemantic(String semanticHint, String source, int offset) {
        String context = (semanticHint + " " + matchedLine(source, Math.max(0, offset))).toLowerCase(Locale.ROOT);
        boolean hasLineToken = context.contains("stroke")
                || context.contains("border")
                || context.contains("outline")
                || context.contains("divider")
                || context.contains("line");
        boolean hasWidthToken = context.contains("width") || context.contains("thickness");
        return hasLineToken && hasWidthToken;
    }

    // 获取命中位置所在行。
    private static String matchedLine(String source, int offset) {
        int start = source.lastIndexOf('\n', Math.max(0, offset - 1));
        int end = source.indexOf('\n', offset);
        int from = start < 0 ? 0 : start + 1;
        int to = end < 0 ? source.length() : end;
        return source.substring(from, to).trim();
    }

    // 统一构建失败信息，避免一次只暴露一条问题。
    private static String buildViolationMessage(String scope, List<String> violations) {
        if (violations.isEmpty()) {
            return scope + " spacing literal 校验通过";
        }
        List<String> uniquePreview = new ArrayList<>(new LinkedHashSet<>(violations));
        int previewCount = Math.min(10, uniquePreview.size());
        List<String> preview = uniquePreview.subList(0, previewCount);
        return "发现 " + scope + " spacing literal 共 " + violations.size() + " 处，示例："
                + System.lineSeparator()
                + String.join(System.lineSeparator(), preview);
    }

    // 压缩语句空白，便于失败时阅读。
    private static String compactStatement(String statement) {
        return statement.replaceAll("\\s+", " ").trim();
    }

    // 压缩表达式空白，便于失败时阅读。
    private static String compactExpression(String expression) {
        return expression.replaceAll("\\s+", " ").trim();
    }

    // 归一化数值集合。
    private static Set<Double> limitAndNormalize(Set<Double> values) {
        if (values.size() > MAX_RESOLVED_VALUE_COUNT) {
            return Collections.emptySet();
        }
        Set<Double> normalized = new LinkedHashSet<>();
        for (double value : values) {
            normalized.add(normalizeValue(value));
        }
        return normalized;
    }

    // 单值转集合。
    private static Set<Double> singletonValue(double value) {
        Set<Double> values = new LinkedHashSet<>();
        values.add(normalizeValue(value));
        return values;
    }

    // 归一化单个数值。
    private static double normalizeValue(double value) {
        if (Math.abs(value) < EPSILON) {
            return 0d;
        }
        if (Math.abs(value - Math.rint(value)) < EPSILON) {
            return Math.rint(value);
        }
        return value;
    }

    // 收集目录中指定后缀的全部文件。
    private static List<Path> collectFiles(Path root, String extension) throws IOException {
        if (!Files.exists(root)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(extension))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    // 收集主链 XML（res/layout*）文件。
    private static List<Path> collectMainChainXmlFiles(Path resRoot) throws IOException {
        List<Path> files = collectFiles(resRoot, ".xml");
        List<Path> mainChainFiles = new ArrayList<>();
        for (Path file : files) {
            Path relativePath = resRoot.relativize(file);
            if (relativePath.getNameCount() == 0) {
                continue;
            }
            String topDir = relativePath.getName(0).toString().toLowerCase(Locale.ROOT);
            if (!topDir.startsWith(MAIN_XML_DIR_PREFIX)) {
                continue;
            }
            mainChainFiles.add(file);
        }
        return mainChainFiles;
    }

    // 判断 Java 文件是否属于主链 UI 包。
    private static boolean isMainChainJavaFile(Path mainRoot, Path file) {
        String relativePath = mainRoot.relativize(file).toString().replace('\\', '/');
        return relativePath.startsWith(MAIN_JAVA_UI_PACKAGE);
    }

    // 解析 src/main 根目录，兼容从仓库根或模块根执行。
    private static Path resolveMainSourceRoot() {
        Path workingDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (String candidate : MAIN_ROOT_CANDIDATES) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return path;
            }
        }
        throw new IllegalStateException("未找到 src/main 根目录");
    }

    // 转换为统一 src/main 相对路径格式。
    private static String toMainRelativePath(Path mainRoot, Path file) {
        return "src/main/" + mainRoot.relativize(file).toString().replace('\\', '/');
    }

    // 读取 UTF-8 文件内容并统一换行。
    private static String readUtf8(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    // 规范化输出数字，便于失败信息阅读。
    private static String formatNumericValues(Set<Double> values) {
        List<String> ordered = new ArrayList<>();
        for (double value : values) {
            ordered.add(formatNumeric(value));
        }
        return String.join("|", ordered);
    }

    // 规范化输出单个数字。
    private static String formatNumeric(double value) {
        if (Math.abs(value - Math.rint(value)) < EPSILON) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.valueOf(value);
    }

    // 解析数字字面量。
    private static double parseNumeric(String text) {
        String normalized = text.trim();
        char last = normalized.charAt(normalized.length() - 1);
        if (last == 'f' || last == 'F' || last == 'd' || last == 'D') {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return Double.parseDouble(normalized);
    }

    // 源码结构：整份源码 + 根作用域 + 语句列表。
    private static final class SourceStructure {
        private final String source;
        private final ScopeInfo rootScope;
        private final List<StatementInfo> statements;

        private SourceStructure(String source, ScopeInfo rootScope, List<StatementInfo> statements) {
            this.source = source;
            this.rootScope = rootScope;
            this.statements = statements;
        }
    }

    // 作用域节点，保存局部声明和子作用域。
    private static final class ScopeInfo {
        private final ScopeInfo parent;
        private final int depth;
        private final int start;
        private int end;
        private final List<ScopeInfo> children = new ArrayList<>();
        private final List<DeclarationInfo> declarations = new ArrayList<>();

        private ScopeInfo(ScopeInfo parent, int depth, int start) {
            this.parent = parent;
            this.depth = depth;
            this.start = start;
        }

        private DeclarationInfo findLocalDeclaration(String name, int offset) {
            for (int i = declarations.size() - 1; i >= 0; i--) {
                DeclarationInfo declaration = declarations.get(i);
                if (!declaration.name.equals(name)) {
                    continue;
                }
                if (declaration.offset < offset) {
                    return declaration;
                }
            }
            return null;
        }

        private DeclarationInfo findClassDeclaration(String name) {
            for (int i = declarations.size() - 1; i >= 0; i--) {
                DeclarationInfo declaration = declarations.get(i);
                if (declaration.name.equals(name)) {
                    return declaration;
                }
            }
            return null;
        }
    }

    // 单条语句信息。
    private static final class StatementInfo {
        private final String text;
        private final int start;
        private final int end;
        private final ScopeInfo scope;

        private StatementInfo(String text, int start, int end, ScopeInfo scope) {
            this.text = text;
            this.start = start;
            this.end = end;
            this.scope = scope;
        }
    }

    // 单条声明信息。
    private static final class DeclarationInfo {
        private final String name;
        private final String expression;
        private final int offset;
        private final ScopeInfo scope;

        private DeclarationInfo(String name, String expression, int offset, ScopeInfo scope) {
            this.name = name;
            this.expression = expression;
            this.offset = offset;
            this.scope = scope;
        }
    }

    // 顶层二元切分结果。
    private static final class BinarySplit {
        private final String left;
        private final String right;
        private final char operator;

        private BinarySplit(String left, String right, char operator) {
            this.left = left.trim();
            this.right = right.trim();
            this.operator = operator;
        }
    }

    // cast 剥离结果。
    private static final class CastSplit {
        private final String typeName;
        private final String expression;

        private CastSplit(String typeName, String expression) {
            this.typeName = typeName;
            this.expression = expression;
        }
    }
}
