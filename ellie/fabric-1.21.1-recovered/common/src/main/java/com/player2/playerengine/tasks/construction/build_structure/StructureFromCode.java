package com.player2.playerengine.tasks.construction.build_structure;

import com.player2.playerengine.PlayerEngineController;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StructureFromCode {
   public static final Logger LOGGER = LogManager.getLogger();

   public static StructureFromCode.Program compile(String source) {
      StructureFromCode.Lexer lex = new StructureFromCode.Lexer(source);
      List<StructureFromCode.Token> tokens = lex.lex();
      StructureFromCode.Parser parser = new StructureFromCode.Parser(tokens);
      List<StructureFromCode.Stmt> stmts = parser.parse();
      return new StructureFromCode.Program(stmts);
   }

   public static void runCode(String var0, Consumer<StructureFromCode.SetBlockCommand> var1, PlayerEngineController var2) throws Exception {
      StructureFromCode.Program var3 = compile(var0);
      StructureFromCode.Runner var4 = new StructureFromCode.Runner(var3);

      Optional var5;
      while ((var5 = var4.next()).isPresent()) {
         if (Thread.currentThread().isInterrupted()) {
            return;
         }

         StructureFromCode.SetBlockCommand var6 = (StructureFromCode.SetBlockCommand)var5.get();
         var1.accept(var6);
      }
   }

   public static void buildStructureFromCode(
      String code, Consumer<StructureFromCode.SetBlockCommand> onSetBlock, Consumer<String> onErrString, Runnable onFinishSuccess, PlayerEngineController mod
   ) {
      try {
         runCode(code, _unused -> {}, mod);
         LOGGER.info("Code validated, running code for real now.");
         runCode(code, onSetBlock, mod);
         onFinishSuccess.run();
      } catch (Exception e) {
         String err = e.getMessage();
         String error = err == null ? "unknown error" : err;
         LOGGER.error("LLM build structure err={} ", error);
         onErrString.accept(error);
      }
   }

   static final class Assign implements StructureFromCode.Expr {
      final StructureFromCode.Token name;
      final StructureFromCode.Expr value;

      Assign(StructureFromCode.Token n, StructureFromCode.Expr v) {
         this.name = n;
         this.value = v;
      }

      @Override
      public <R> R accept(StructureFromCode.ExprVisitor<R> v) {
         return v.visitAssign(this);
      }
   }

   static final class Binary implements StructureFromCode.Expr {
      final StructureFromCode.Expr left;
      final StructureFromCode.Token op;
      final StructureFromCode.Expr right;

      Binary(StructureFromCode.Expr l, StructureFromCode.Token o, StructureFromCode.Expr r) {
         this.left = l;
         this.op = o;
         this.right = r;
      }

      @Override
      public <R> R accept(StructureFromCode.ExprVisitor<R> v) {
         return v.visitBinary(this);
      }
   }

   static final class Block implements StructureFromCode.Stmt {
      final List<StructureFromCode.Stmt> statements;

      Block(List<StructureFromCode.Stmt> s) {
         this.statements = s;
      }

      @Override
      public void accept(StructureFromCode.StmtVisitor v) {
         v.visitBlockStmt(this);
      }
   }

   static final class Conditional implements StructureFromCode.Expr {
      final StructureFromCode.Expr condition;
      final StructureFromCode.Expr thenExpr;
      final StructureFromCode.Expr elseExpr;

      Conditional(StructureFromCode.Expr condition, StructureFromCode.Expr thenExpr, StructureFromCode.Expr elseExpr) {
         this.condition = condition;
         this.thenExpr = thenExpr;
         this.elseExpr = elseExpr;
      }

      @Override
      public <R> R accept(StructureFromCode.ExprVisitor<R> v) {
         return v.visitConditional(this);
      }
   }

   static final class Env {
      private final StructureFromCode.Env enclosing;
      private final Map<String, Object> values = new HashMap<>();

      Env(StructureFromCode.Env enclosing) {
         this.enclosing = enclosing;
      }

      void define(String name, Object value) {
         this.values.put(name, value);
      }

      Object get(StructureFromCode.Token nameTok) {
         String name = nameTok.lexeme;
         if (this.values.containsKey(name)) {
            return this.values.get(name);
         } else if (this.enclosing != null) {
            return this.enclosing.get(nameTok);
         } else {
            throw new RuntimeException("Undefined variable '" + name + "'.");
         }
      }

      void assign(StructureFromCode.Token nameTok, Object value) {
         String name = nameTok.lexeme;
         if (this.values.containsKey(name)) {
            this.values.put(name, value);
         } else if (this.enclosing != null) {
            this.enclosing.assign(nameTok, value);
         } else {
            throw new RuntimeException("Undefined variable '" + name + "'.");
         }
      }
   }

   interface Expr {
      <R> R accept(StructureFromCode.ExprVisitor<R> var1);
   }

   static final class ExprStmt implements StructureFromCode.Stmt {
      final StructureFromCode.Expr expr;

      ExprStmt(StructureFromCode.Expr e) {
         this.expr = e;
      }

      @Override
      public void accept(StructureFromCode.StmtVisitor v) {
         v.visitExprStmt(this);
      }
   }

   interface ExprVisitor<R> {
      R visitBinary(StructureFromCode.Binary var1);

      R visitUnary(StructureFromCode.Unary var1);

      R visitLiteral(StructureFromCode.Literal var1);

      R visitGrouping(StructureFromCode.Grouping var1);

      R visitVariable(StructureFromCode.Variable var1);

      R visitAssign(StructureFromCode.Assign var1);

      R visitConditional(StructureFromCode.Conditional var1);
   }

   static final class For implements StructureFromCode.Stmt {
      final StructureFromCode.Stmt initializer;
      final StructureFromCode.Expr condition;
      final StructureFromCode.Stmt increment;
      final StructureFromCode.Stmt body;

      For(StructureFromCode.Stmt init, StructureFromCode.Expr cond, StructureFromCode.Stmt inc, StructureFromCode.Stmt body) {
         this.initializer = init;
         this.condition = cond;
         this.increment = inc;
         this.body = body;
      }

      @Override
      public void accept(StructureFromCode.StmtVisitor v) {
         v.visitForStmt(this);
      }
   }

   static final class Grouping implements StructureFromCode.Expr {
      final StructureFromCode.Expr expr;

      Grouping(StructureFromCode.Expr e) {
         this.expr = e;
      }

      @Override
      public <R> R accept(StructureFromCode.ExprVisitor<R> v) {
         return v.visitGrouping(this);
      }
   }

   static final class If implements StructureFromCode.Stmt {
      final StructureFromCode.Expr condition;
      final StructureFromCode.Stmt thenBranch;
      final StructureFromCode.Stmt elseBranch;

      If(StructureFromCode.Expr c, StructureFromCode.Stmt t, StructureFromCode.Stmt e) {
         this.condition = c;
         this.thenBranch = t;
         this.elseBranch = e;
      }

      @Override
      public void accept(StructureFromCode.StmtVisitor v) {
         v.visitIfStmt(this);
      }
   }

   static final class Interpreter implements StructureFromCode.ExprVisitor<Object>, StructureFromCode.StmtVisitor {
      private final StructureFromCode.Program program;
      private final StructureFromCode.Interpreter.Emitter emitter;
      private final Deque<StructureFromCode.Env> envStack = new ArrayDeque<>();
      private final Deque<StructureFromCode.Interpreter.Frame> frames = new ArrayDeque<>();
      private boolean initialized = false;
      private int emittedCount = 0;
      private int physicalLoopCount;

      Interpreter(StructureFromCode.Program program, StructureFromCode.Interpreter.Emitter emitter) {
         this.program = program;
         this.emitter = emitter;
         this.envStack.push(new StructureFromCode.Env(null));
      }

      boolean step() {
         if (!this.initialized) {
            this.frames.push(new StructureFromCode.Interpreter.Frame(this.program.statements));
            this.initialized = true;
         }

         while (!this.frames.isEmpty()) {
            StructureFromCode.Interpreter.Frame f = this.frames.peek();
            if (f instanceof StructureFromCode.Interpreter.WhileFrame wf) {
               if (isTruthy(this.evaluate(wf.condition))) {
                  this.frames.push(new StructureFromCode.Interpreter.Frame(singleton(wf.body)));
                  this.checkPhysicalLoop();
               } else {
                  this.frames.pop();
                  this.checkPhysicalLoop();
               }
            } else if (f.ip >= f.stmts.size()) {
               this.frames.pop();
               Runnable var10000 = f.onClose;
               this.checkPhysicalLoop();
               if (var10000 != null) {
                  f.onClose.run();
                  this.checkPhysicalLoop();
               }
            } else {
               StructureFromCode.Stmt s = f.stmts.get(f.ip++);
               int emittedBefore = this.emittedCount;
               s.accept(this);
               if (this.emittedCount > emittedBefore) {
                  return true;
               }

               this.checkPhysicalLoop();
            }
         }

         return false;
      }

      private void emit(StructureFromCode.SetBlockCommand cmd) {
         this.emittedCount++;
         this.emitter.emit(cmd);
      }

      @Override
      public void visitExprStmt(StructureFromCode.ExprStmt s) {
         this.evaluate(s.expr);
      }

      @Override
      public void visitPrintStmt(StructureFromCode.PrintStmt s) {
         System.out.println(stringify(this.evaluate(s.expr)));
      }

      @Override
      public void visitVarStmt(StructureFromCode.Var s) {
         Object val = this.evaluate(s.initializer);
         this.env().define(s.name.lexeme, val);
      }

      @Override
      public void visitBlockStmt(StructureFromCode.Block s) {
         this.pushEnv();
         this.frames.push(new StructureFromCode.Interpreter.Frame(s.statements, () -> this.popEnv()));
      }

      @Override
      public void visitIfStmt(StructureFromCode.If s) {
         if (isTruthy(this.evaluate(s.condition))) {
            this.frames.push(new StructureFromCode.Interpreter.Frame(singleton(s.thenBranch)));
         } else if (s.elseBranch != null) {
            this.frames.push(new StructureFromCode.Interpreter.Frame(singleton(s.elseBranch)));
         }
      }

      @Override
      public void visitWhileStmt(StructureFromCode.While s) {
         this.frames.push(new StructureFromCode.Interpreter.WhileFrame(s.condition, s.body));
      }

      @Override
      public void visitForStmt(StructureFromCode.For s) {
         this.frames.push(new StructureFromCode.Interpreter.Frame(singleton(s.body)));
      }

      @Override
      public void visitSetBlockStmt(StructureFromCode.SetBlock s) {
         int x = toInt(this.evaluate(s.x));
         int y = toInt(this.evaluate(s.y));
         int z = toInt(this.evaluate(s.z));
         String block = String.valueOf(this.evaluate(s.block));
         this.emit(new StructureFromCode.SetBlockCommand(x, y, z, block));
      }

      @Override
      public Object visitBinary(StructureFromCode.Binary e) {
         if (e.op.type == StructureFromCode.TokenType.OR_OR) {
            Object l = this.evaluate(e.left);
            return isTruthy(l) ? true : isTruthy(this.evaluate(e.right));
         }

         if (e.op.type == StructureFromCode.TokenType.AND_AND) {
            Object l = this.evaluate(e.left);
            return !isTruthy(l) ? false : isTruthy(this.evaluate(e.right));
         }

         Object l = this.evaluate(e.left);
         Object r = this.evaluate(e.right);
         switch (e.op.type) {
            case MINUS:
               checkNumber(l, e.op);
               checkNumber(r, e.op);
               return (Double)l - (Double)r;
            case PLUS:
               if (l instanceof Double && r instanceof Double) {
                  return (Double)l + (Double)r;
               }

               return stringify(l) + stringify(r);
            case PERCENT:
               checkNumber(l, e.op);
               checkNumber(r, e.op);
               return (Double)l % (Double)r;
            case SEMICOLON:
            case BANG:
            case EQUAL:
            default:
               throw new RuntimeException("Unknown binary op: " + e.op.type);
            case SLASH:
               checkNumber(l, e.op);
               checkNumber(r, e.op);
               return (Double)l / (Double)r;
            case STAR:
               checkNumber(l, e.op);
               checkNumber(r, e.op);
               return (Double)l * (Double)r;
            case BANG_EQUAL:
               return !isEqual(l, r);
            case EQUAL_EQUAL:
               return isEqual(l, r);
            case GREATER:
               checkNumber(l, e.op);
               checkNumber(r, e.op);
               return (Double)l > (Double)r;
            case GREATER_EQUAL:
               checkNumber(l, e.op);
               checkNumber(r, e.op);
               return (Double)l >= (Double)r;
            case LESS:
               checkNumber(l, e.op);
               checkNumber(r, e.op);
               return (Double)l < (Double)r;
            case LESS_EQUAL:
               checkNumber(l, e.op);
               checkNumber(r, e.op);
               return (Double)l <= (Double)r;
         }
      }

      @Override
      public Object visitConditional(StructureFromCode.Conditional e) {
         Object cond = this.evaluate(e.condition);
         return isTruthy(cond) ? this.evaluate(e.thenExpr) : this.evaluate(e.elseExpr);
      }

      @Override
      public Object visitUnary(StructureFromCode.Unary e) {
         Object v = this.evaluate(e.right);
         switch (e.op.type) {
            case MINUS:
               checkNumber(v, e.op);
               return -(Double)v;
            case BANG:
               return !isTruthy(v);
            default:
               throw new RuntimeException("Unknown unary op: " + e.op.type);
         }
      }

      @Override
      public Object visitLiteral(StructureFromCode.Literal e) {
         return e.value;
      }

      @Override
      public Object visitGrouping(StructureFromCode.Grouping e) {
         return this.evaluate(e.expr);
      }

      @Override
      public Object visitVariable(StructureFromCode.Variable e) {
         return this.env().get(e.name);
      }

      @Override
      public Object visitAssign(StructureFromCode.Assign e) {
         Object val = this.evaluate(e.value);
         this.env().assign(e.name, val);
         return val;
      }

      private Object evaluate(StructureFromCode.Expr e) {
         return e.accept(this);
      }

      private StructureFromCode.Env env() {
         return this.envStack.peek();
      }

      private void pushEnv() {
         this.envStack.push(new StructureFromCode.Env(this.env()));
      }

      private void popEnv() {
         this.envStack.pop();
      }

      private static boolean isTruthy(Object v) {
         if (v == null) {
            return false;
         }

         if (v instanceof Boolean) {
            return (Boolean)v;
         }

         if (v instanceof Double) {
            return (Double)v != 0.0;
         }

         String s = String.valueOf(v);
         return !s.isEmpty();
      }

      private static boolean isEqual(Object a, Object b) {
         if (a == null && b == null) {
            return true;
         } else if (a == null) {
            return false;
         } else {
            return a instanceof Double && b instanceof Double ? (Double)a == (Double)b : String.valueOf(a).equals(String.valueOf(b));
         }
      }

      private static void checkNumber(Object v, StructureFromCode.Token at) {
         if (!(v instanceof Double)) {
            throw new RuntimeException("Operand must be a number at token '" + at.lexeme + "'");
         }
      }

      private static String stringify(Object v) {
         if (v == null) {
            return "nil";
         } else if (v instanceof Double) {
            double d = (Double)v;
            return d == Math.rint(d) ? String.valueOf((long)d) : String.valueOf(d);
         } else {
            return String.valueOf(v);
         }
      }

      private static int toInt(Object v) {
         if (v instanceof Double) {
            return (int)Math.round((Double)v);
         }

         try {
            return Integer.parseInt(String.valueOf(v));
         } catch (Exception e) {
            throw new RuntimeException("Expected integer-like value, got: " + v);
         }
      }

      private static List<StructureFromCode.Stmt> singleton(StructureFromCode.Stmt s) {
         return Collections.singletonList(s);
      }

      private void checkPhysicalLoop() {
         if (++this.physicalLoopCount > 100000) {
            throw new IllegalArgumentException("AI building script exceeded safe loop budget (100000)");
         }

         if (Thread.currentThread().isInterrupted()) {
            throw new IllegalArgumentException("AI building script cancelled during execution");
         }
      }

      interface Emitter {
         void emit(StructureFromCode.SetBlockCommand var1);
      }

      static class Frame {
         final List<StructureFromCode.Stmt> stmts;
         int ip = 0;
         final Runnable onClose;

         Frame(List<StructureFromCode.Stmt> stmts) {
            this(stmts, null);
         }

         Frame(List<StructureFromCode.Stmt> stmts, Runnable onClose) {
            this.stmts = stmts;
            this.onClose = onClose;
         }
      }

      static final class WhileFrame extends StructureFromCode.Interpreter.Frame {
         final StructureFromCode.Expr condition;
         final StructureFromCode.Stmt body;

         WhileFrame(StructureFromCode.Expr condition, StructureFromCode.Stmt body) {
            super(Collections.emptyList());
            this.condition = condition;
            this.body = body;
         }
      }
   }

   static final class Lexer {
      private final String src;
      private final List<StructureFromCode.Token> tokens = new ArrayList<>();
      private int start = 0;
      private int current = 0;
      private int line = 1;
      private int col = 1;
      private static final Map<String, StructureFromCode.TokenType> keywords = new HashMap<>();

      Lexer(String src) {
         this.src = src;
      }

      List<StructureFromCode.Token> lex() {
         while (!this.isAtEnd()) {
            this.start = this.current;
            this.scanToken();
         }

         this.tokens.add(new StructureFromCode.Token(StructureFromCode.TokenType.EOF, "", null, this.line, this.col));
         return this.tokens;
      }

      private void scanToken() {
         char c = this.advance();
         switch (c) {
            case '\t':
            case '\r':
            case ' ':
               break;
            case '\n':
               this.line++;
               this.col = 0;
               break;
            case '!':
               this.add(this.match('=') ? StructureFromCode.TokenType.BANG_EQUAL : StructureFromCode.TokenType.BANG);
               break;
            case '"':
               this.string();
               break;
            case '%':
               this.add(StructureFromCode.TokenType.PERCENT);
               break;
            case '&':
               if (this.match('&')) {
                  this.add(StructureFromCode.TokenType.AND_AND);
               } else {
                  this.error("Unexpected character: & (did you mean &&?)");
               }
               break;
            case '(':
               this.add(StructureFromCode.TokenType.LEFT_PAREN);
               break;
            case ')':
               this.add(StructureFromCode.TokenType.RIGHT_PAREN);
               break;
            case '*':
               this.add(StructureFromCode.TokenType.STAR);
               break;
            case '+':
               this.add(StructureFromCode.TokenType.PLUS);
               break;
            case ',':
               this.add(StructureFromCode.TokenType.COMMA);
               break;
            case '-':
               this.add(StructureFromCode.TokenType.MINUS);
               break;
            case '.':
               this.add(StructureFromCode.TokenType.DOT);
               break;
            case '/':
               if (this.match('/')) {
                  while (!this.isAtEnd() && this.peek() != '\n') {
                     this.advance();
                  }
               } else if (!this.match('*')) {
                  this.add(StructureFromCode.TokenType.SLASH);
               } else {
                  for (; !this.isAtEnd() && (this.peek() != '*' || this.peekNext() != '/'); this.advance()) {
                     if (this.peek() == '\n') {
                        this.line++;
                        this.col = 0;
                     }
                  }

                  if (!this.isAtEnd()) {
                     this.advance();
                     this.advance();
                  }
               }
               break;
            case ':':
               this.add(StructureFromCode.TokenType.COLON);
               break;
            case ';':
               this.add(StructureFromCode.TokenType.SEMICOLON);
               break;
            case '<':
               this.add(this.match('=') ? StructureFromCode.TokenType.LESS_EQUAL : StructureFromCode.TokenType.LESS);
               break;
            case '=':
               this.add(this.match('=') ? StructureFromCode.TokenType.EQUAL_EQUAL : StructureFromCode.TokenType.EQUAL);
               break;
            case '>':
               this.add(this.match('=') ? StructureFromCode.TokenType.GREATER_EQUAL : StructureFromCode.TokenType.GREATER);
               break;
            case '?':
               this.add(StructureFromCode.TokenType.QUESTION);
               break;
            case '{':
               this.add(StructureFromCode.TokenType.LEFT_BRACE);
               break;
            case '|':
               if (this.match('|')) {
                  this.add(StructureFromCode.TokenType.OR_OR);
               } else {
                  this.error("Unexpected character: | (did you mean ||?)");
               }
               break;
            case '}':
               this.add(StructureFromCode.TokenType.RIGHT_BRACE);
               break;
            default:
               if (isDigit(c)) {
                  this.number();
               } else if (isAlpha(c)) {
                  this.identifier();
               } else {
                  this.error("Unexpected character: " + c);
               }
         }
      }

      private void string() {
         StringBuilder sb = new StringBuilder();

         while (!this.isAtEnd() && this.peek() != '"') {
            char c = this.advance();
            if (c == '\\') {
               if (this.isAtEnd()) {
                  break;
               }

               char n = this.advance();
               switch (n) {
                  case '"':
                     sb.append('"');
                     break;
                  case '\\':
                     sb.append('\\');
                     break;
                  case 'n':
                     sb.append('\n');
                     break;
                  case 't':
                     sb.append('\t');
                     break;
                  default:
                     sb.append(n);
               }
            } else {
               sb.append(c);
            }

            if (c == '\n') {
               this.line++;
               this.col = 0;
            }
         }

         if (this.isAtEnd()) {
            this.error("Unterminated string.");
         }

         this.advance();
         this.add(StructureFromCode.TokenType.STRING, sb.toString());
      }

      private void number() {
         while (isDigit(this.peek())) {
            this.advance();
         }

         if (this.peek() == '.' && isDigit(this.peekNext())) {
            this.advance();

            while (isDigit(this.peek())) {
               this.advance();
            }
         }

         double val = Double.parseDouble(this.src.substring(this.start, this.current));
         this.add(StructureFromCode.TokenType.NUMBER, val);
      }

      private void identifier() {
         while (isAlphaNumeric(this.peek())) {
            this.advance();
         }

         String text = this.src.substring(this.start, this.current);
         StructureFromCode.TokenType type = keywords.get(text);
         if ("setBlock".equals(text)) {
            type = StructureFromCode.TokenType.SETBLOCK;
         }

         if (type == null) {
            type = StructureFromCode.TokenType.IDENTIFIER;
         }

         this.add(type);
      }

      private void add(StructureFromCode.TokenType type) {
         this.add(type, null);
      }

      private void add(StructureFromCode.TokenType type, Object lit) {
         this.tokens.add(new StructureFromCode.Token(type, this.src.substring(this.start, this.current), lit, this.line, this.col));
      }

      private boolean match(char expected) {
         if (!this.isAtEnd() && this.src.charAt(this.current) == expected) {
            this.advance();
            return true;
         } else {
            return false;
         }
      }

      private char peek() {
         return this.isAtEnd() ? '\u0000' : this.src.charAt(this.current);
      }

      private char peekNext() {
         return this.current + 1 >= this.src.length() ? '\u0000' : this.src.charAt(this.current + 1);
      }

      private char advance() {
         this.current++;
         this.col++;
         return this.src.charAt(this.current - 1);
      }

      private boolean isAtEnd() {
         return this.current >= this.src.length();
      }

      private static boolean isDigit(char c) {
         return c >= '0' && c <= '9';
      }

      private static boolean isAlpha(char c) {
         return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_';
      }

      private static boolean isAlphaNumeric(char c) {
         return isAlpha(c) || isDigit(c);
      }

      private void error(String msg) {
         throw new RuntimeException("[Lexer] line " + this.line + ", col " + this.col + ": " + msg);
      }

      static {
         keywords.put("let", StructureFromCode.TokenType.LET);
         keywords.put("if", StructureFromCode.TokenType.IF);
         keywords.put("else", StructureFromCode.TokenType.ELSE);
         keywords.put("while", StructureFromCode.TokenType.WHILE);
         keywords.put("for", StructureFromCode.TokenType.FOR);
         keywords.put("true", StructureFromCode.TokenType.TRUE);
         keywords.put("false", StructureFromCode.TokenType.FALSE);
         keywords.put("nil", StructureFromCode.TokenType.NIL);
      }
   }

   static final class Literal implements StructureFromCode.Expr {
      final Object value;

      Literal(Object v) {
         this.value = v;
      }

      @Override
      public <R> R accept(StructureFromCode.ExprVisitor<R> v) {
         return v.visitLiteral(this);
      }
   }

   static final class Parser {
      private final List<StructureFromCode.Token> tokens;
      private int current = 0;

      Parser(List<StructureFromCode.Token> tokens) {
         this.tokens = tokens;
      }

      List<StructureFromCode.Stmt> parse() {
         List<StructureFromCode.Stmt> stmts = new ArrayList<>();

         while (!this.isAtEnd()) {
            stmts.add(this.declaration());
         }

         return stmts;
      }

      private StructureFromCode.Stmt declaration() {
         return this.match(StructureFromCode.TokenType.LET) ? this.varDeclaration() : this.statement();
      }

      private StructureFromCode.Stmt varDeclaration() {
         StructureFromCode.Token name = this.consume(StructureFromCode.TokenType.IDENTIFIER, "Expect variable name.");
         StructureFromCode.Expr init = null;
         if (this.match(StructureFromCode.TokenType.EQUAL)) {
            init = this.expression();
         }

         this.consume(StructureFromCode.TokenType.SEMICOLON, "Expect ';' after variable declaration.");
         return new StructureFromCode.Var(name, init == null ? new StructureFromCode.Literal(null) : init);
      }

      private StructureFromCode.Stmt statement() {
         if (this.match(StructureFromCode.TokenType.LEFT_BRACE)) {
            return new StructureFromCode.Block(this.block());
         } else if (this.match(StructureFromCode.TokenType.IF)) {
            return this.ifStatement();
         } else if (this.match(StructureFromCode.TokenType.WHILE)) {
            return this.whileStatement();
         } else if (this.match(StructureFromCode.TokenType.FOR)) {
            return this.forStatement();
         } else {
            return this.match(StructureFromCode.TokenType.SETBLOCK) ? this.setBlockStatement() : this.exprStatement();
         }
      }

      private StructureFromCode.Stmt setBlockStatement() {
         this.consume(StructureFromCode.TokenType.LEFT_PAREN, "Expect '(' after setBlock.");
         StructureFromCode.Expr x = this.expression();
         this.consume(StructureFromCode.TokenType.COMMA, "Expect ',' after x.");
         StructureFromCode.Expr y = this.expression();
         this.consume(StructureFromCode.TokenType.COMMA, "Expect ',' after y.");
         StructureFromCode.Expr z = this.expression();
         this.consume(StructureFromCode.TokenType.COMMA, "Expect ',' after z.");
         StructureFromCode.Expr block = this.expression();
         this.consume(StructureFromCode.TokenType.RIGHT_PAREN, "Expect ')' after arguments.");
         this.consume(StructureFromCode.TokenType.SEMICOLON, "Expect ';' after setBlock.");
         return new StructureFromCode.SetBlock(x, y, z, block);
      }

      private StructureFromCode.Stmt ifStatement() {
         this.consume(StructureFromCode.TokenType.LEFT_PAREN, "Expect '(' after if.");
         StructureFromCode.Expr cond = this.expression();
         this.consume(StructureFromCode.TokenType.RIGHT_PAREN, "Expect ')' after condition.");
         StructureFromCode.Stmt thenB = this.statement();
         StructureFromCode.Stmt elseB = null;
         if (this.match(StructureFromCode.TokenType.ELSE)) {
            elseB = this.statement();
         }

         return new StructureFromCode.If(cond, thenB, elseB);
      }

      private StructureFromCode.Stmt whileStatement() {
         this.consume(StructureFromCode.TokenType.LEFT_PAREN, "Expect '(' after while.");
         StructureFromCode.Expr cond = this.expression();
         this.consume(StructureFromCode.TokenType.RIGHT_PAREN, "Expect ')' after condition.");
         StructureFromCode.Stmt body = this.statement();
         return new StructureFromCode.While(cond, body);
      }

      private StructureFromCode.Stmt forStatement() {
         this.consume(StructureFromCode.TokenType.LEFT_PAREN, "Expect '(' after for.");
         StructureFromCode.Stmt init;
         if (this.match(StructureFromCode.TokenType.SEMICOLON)) {
            init = null;
         } else if (this.match(StructureFromCode.TokenType.LET)) {
            init = this.varDeclarationNoSemi();
            this.consume(StructureFromCode.TokenType.SEMICOLON, "Expect ';' after for init.");
         } else {
            init = this.exprStatementNoSemi();
            this.consume(StructureFromCode.TokenType.SEMICOLON, "Expect ';' after for init.");
         }

         StructureFromCode.Expr cond = null;
         if (!this.check(StructureFromCode.TokenType.SEMICOLON)) {
            cond = this.expression();
         }

         this.consume(StructureFromCode.TokenType.SEMICOLON, "Expect ';' after loop condition.");
         StructureFromCode.Stmt inc = null;
         if (!this.check(StructureFromCode.TokenType.RIGHT_PAREN)) {
            inc = this.exprStatementNoSemi();
         }

         this.consume(StructureFromCode.TokenType.RIGHT_PAREN, "Expect ')' after for clauses.");
         StructureFromCode.Stmt body = this.statement();
         if (inc != null) {
            body = new StructureFromCode.Block(Arrays.asList(body, inc));
         }

         if (cond == null) {
            cond = new StructureFromCode.Literal(true);
         }

         StructureFromCode.Stmt whileStmt = new StructureFromCode.While(cond, body);
         if (init != null) {
            whileStmt = new StructureFromCode.Block(Arrays.asList(init, whileStmt));
         }

         return whileStmt;
      }

      private StructureFromCode.Stmt exprStatement() {
         StructureFromCode.Expr e = this.expression();
         this.consume(StructureFromCode.TokenType.SEMICOLON, "Expect ';' after expression.");
         return new StructureFromCode.ExprStmt(e);
      }

      private StructureFromCode.Stmt exprStatementNoSemi() {
         StructureFromCode.Expr e = this.expression();
         return new StructureFromCode.ExprStmt(e);
      }

      private StructureFromCode.Stmt varDeclarationNoSemi() {
         StructureFromCode.Token name = this.consume(StructureFromCode.TokenType.IDENTIFIER, "Expect variable name.");
         StructureFromCode.Expr init = null;
         if (this.match(StructureFromCode.TokenType.EQUAL)) {
            init = this.expression();
         }

         return new StructureFromCode.Var(name, init == null ? new StructureFromCode.Literal(null) : init);
      }

      private List<StructureFromCode.Stmt> block() {
         List<StructureFromCode.Stmt> stmts = new ArrayList<>();

         while (!this.check(StructureFromCode.TokenType.RIGHT_BRACE) && !this.isAtEnd()) {
            stmts.add(this.declaration());
         }

         this.consume(StructureFromCode.TokenType.RIGHT_BRACE, "Expect '}' after block.");
         return stmts;
      }

      private StructureFromCode.Expr expression() {
         return this.assignment();
      }

      private StructureFromCode.Expr assignment() {
         StructureFromCode.Expr expr = this.conditional();
         if (this.match(StructureFromCode.TokenType.EQUAL)) {
            StructureFromCode.Token equals = this.previous();
            StructureFromCode.Expr value = this.assignment();
            if (expr instanceof StructureFromCode.Variable) {
               StructureFromCode.Token name = ((StructureFromCode.Variable)expr).name;
               return new StructureFromCode.Assign(name, value);
            }

            this.error(equals, "Invalid assignment target.");
         }

         return expr;
      }

      private StructureFromCode.Expr conditional() {
         StructureFromCode.Expr expr = this.or();
         if (this.match(StructureFromCode.TokenType.QUESTION)) {
            StructureFromCode.Expr thenExpr = this.expression();
            this.consume(StructureFromCode.TokenType.COLON, "Expect ':' in ternary expression.");
            StructureFromCode.Expr elseExpr = this.conditional();
            expr = new StructureFromCode.Conditional(expr, thenExpr, elseExpr);
         }

         return expr;
      }

      private StructureFromCode.Expr or() {
         StructureFromCode.Expr expr = this.and();

         while (this.match(StructureFromCode.TokenType.OR_OR)) {
            StructureFromCode.Token op = this.previous();
            StructureFromCode.Expr right = this.and();
            expr = new StructureFromCode.Binary(expr, op, right);
         }

         return expr;
      }

      private StructureFromCode.Expr and() {
         StructureFromCode.Expr expr = this.equality();

         while (this.match(StructureFromCode.TokenType.AND_AND)) {
            StructureFromCode.Token op = this.previous();
            StructureFromCode.Expr right = this.equality();
            expr = new StructureFromCode.Binary(expr, op, right);
         }

         return expr;
      }

      private StructureFromCode.Expr equality() {
         StructureFromCode.Expr expr = this.comparison();

         while (this.match(StructureFromCode.TokenType.BANG_EQUAL, StructureFromCode.TokenType.EQUAL_EQUAL)) {
            StructureFromCode.Token op = this.previous();
            StructureFromCode.Expr right = this.comparison();
            expr = new StructureFromCode.Binary(expr, op, right);
         }

         return expr;
      }

      private StructureFromCode.Expr comparison() {
         StructureFromCode.Expr expr = this.term();

         while (
            this.match(
               StructureFromCode.TokenType.GREATER,
               StructureFromCode.TokenType.GREATER_EQUAL,
               StructureFromCode.TokenType.LESS,
               StructureFromCode.TokenType.LESS_EQUAL
            )
         ) {
            StructureFromCode.Token op = this.previous();
            StructureFromCode.Expr right = this.term();
            expr = new StructureFromCode.Binary(expr, op, right);
         }

         return expr;
      }

      private StructureFromCode.Expr term() {
         StructureFromCode.Expr expr = this.factor();

         while (this.match(StructureFromCode.TokenType.PLUS, StructureFromCode.TokenType.MINUS)) {
            StructureFromCode.Token op = this.previous();
            StructureFromCode.Expr right = this.factor();
            expr = new StructureFromCode.Binary(expr, op, right);
         }

         return expr;
      }

      private StructureFromCode.Expr factor() {
         StructureFromCode.Expr expr = this.unary();

         while (this.match(StructureFromCode.TokenType.STAR, StructureFromCode.TokenType.SLASH, StructureFromCode.TokenType.PERCENT)) {
            StructureFromCode.Token op = this.previous();
            StructureFromCode.Expr right = this.unary();
            expr = new StructureFromCode.Binary(expr, op, right);
         }

         return expr;
      }

      private StructureFromCode.Expr unary() {
         if (this.match(StructureFromCode.TokenType.BANG, StructureFromCode.TokenType.MINUS)) {
            StructureFromCode.Token op = this.previous();
            StructureFromCode.Expr right = this.unary();
            return new StructureFromCode.Unary(op, right);
         } else {
            return this.primary();
         }
      }

      private StructureFromCode.Expr primary() {
         if (this.match(StructureFromCode.TokenType.FALSE)) {
            return new StructureFromCode.Literal(false);
         } else if (this.match(StructureFromCode.TokenType.TRUE)) {
            return new StructureFromCode.Literal(true);
         } else if (this.match(StructureFromCode.TokenType.NIL)) {
            return new StructureFromCode.Literal(null);
         } else if (this.match(StructureFromCode.TokenType.NUMBER)) {
            return new StructureFromCode.Literal(this.previous().literal);
         } else if (this.match(StructureFromCode.TokenType.STRING)) {
            return new StructureFromCode.Literal(this.previous().literal);
         } else if (this.match(StructureFromCode.TokenType.IDENTIFIER)) {
            return new StructureFromCode.Variable(this.previous());
         } else if (this.match(StructureFromCode.TokenType.LEFT_PAREN)) {
            StructureFromCode.Expr e = this.expression();
            this.consume(StructureFromCode.TokenType.RIGHT_PAREN, "Expect ')' after expression.");
            return new StructureFromCode.Grouping(e);
         } else {
            this.error(this.peek(), "Expect expression.");
            return null;
         }
      }

      private boolean match(StructureFromCode.TokenType... types) {
         for (StructureFromCode.TokenType t : types) {
            if (this.check(t)) {
               this.advance();
               return true;
            }
         }

         return false;
      }

      private boolean check(StructureFromCode.TokenType t) {
         return !this.isAtEnd() && this.peek().type == t;
      }

      private StructureFromCode.Token advance() {
         if (!this.isAtEnd()) {
            this.current++;
         }

         return this.previous();
      }

      private boolean isAtEnd() {
         return this.peek().type == StructureFromCode.TokenType.EOF;
      }

      private StructureFromCode.Token peek() {
         return this.tokens.get(this.current);
      }

      private StructureFromCode.Token previous() {
         return this.tokens.get(this.current - 1);
      }

      private StructureFromCode.Token consume(StructureFromCode.TokenType t, String msg) {
         if (this.check(t)) {
            return this.advance();
         }

         this.error(this.peek(), msg);
         return null;
      }

      private void error(StructureFromCode.Token t, String msg) {
         throw new RuntimeException("[Parser] line " + t.line + ", col " + t.col + ": " + msg + " Found '" + t.lexeme + "'");
      }
   }

   static final class PrintStmt implements StructureFromCode.Stmt {
      final StructureFromCode.Expr expr;

      PrintStmt(StructureFromCode.Expr e) {
         this.expr = e;
      }

      @Override
      public void accept(StructureFromCode.StmtVisitor v) {
         v.visitPrintStmt(this);
      }
   }

   public static final class Program {
      final List<StructureFromCode.Stmt> statements;

      Program(List<StructureFromCode.Stmt> statements) {
         this.statements = statements;
      }
   }

   public static final class Runner {
      private final StructureFromCode.Interpreter interp;
      private final Queue<StructureFromCode.SetBlockCommand> queue = new ArrayDeque<>();
      private boolean done = false;

      public Runner(StructureFromCode.Program program) {
         this.interp = new StructureFromCode.Interpreter(program, this.queue::add);
      }

      public Optional<StructureFromCode.SetBlockCommand> next() {
         if (this.done && this.queue.isEmpty()) {
            return Optional.empty();
         }

         if (!this.queue.isEmpty()) {
            return Optional.of(this.queue.poll());
         }

         while (this.queue.isEmpty() && !this.done) {
            this.done = !this.interp.step();
         }

         return this.queue.isEmpty() ? Optional.empty() : Optional.of(this.queue.poll());
      }
   }

   static final class SetBlock implements StructureFromCode.Stmt {
      final StructureFromCode.Expr x;
      final StructureFromCode.Expr y;
      final StructureFromCode.Expr z;
      final StructureFromCode.Expr block;

      SetBlock(StructureFromCode.Expr x, StructureFromCode.Expr y, StructureFromCode.Expr z, StructureFromCode.Expr block) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.block = block;
      }

      @Override
      public void accept(StructureFromCode.StmtVisitor v) {
         v.visitSetBlockStmt(this);
      }
   }

   public static final class SetBlockCommand {
      public final int x;
      public final int y;
      public final int z;
      public final String blockName;

      public SetBlockCommand(int x, int y, int z, String blockName) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.blockName = blockName;
      }

      @Override
      public String toString() {
         return "setBlock(" + this.x + ", " + this.y + ", " + this.z + ", \"" + this.blockName + "\")";
      }
   }

   interface Stmt {
      void accept(StructureFromCode.StmtVisitor var1);
   }

   interface StmtVisitor {
      void visitExprStmt(StructureFromCode.ExprStmt var1);

      void visitPrintStmt(StructureFromCode.PrintStmt var1);

      void visitVarStmt(StructureFromCode.Var var1);

      void visitBlockStmt(StructureFromCode.Block var1);

      void visitIfStmt(StructureFromCode.If var1);

      void visitWhileStmt(StructureFromCode.While var1);

      void visitForStmt(StructureFromCode.For var1);

      void visitSetBlockStmt(StructureFromCode.SetBlock var1);
   }

   static final class Token {
      final StructureFromCode.TokenType type;
      final String lexeme;
      final Object literal;
      final int line;
      final int col;

      Token(StructureFromCode.TokenType type, String lexeme, Object literal, int line, int col) {
         this.type = type;
         this.lexeme = lexeme;
         this.literal = literal;
         this.line = line;
         this.col = col;
      }

      @Override
      public String toString() {
         return this.type + " '" + this.lexeme + "'" + (this.literal != null ? " -> " + this.literal : "");
      }
   }

   enum TokenType {
      LEFT_PAREN,
      RIGHT_PAREN,
      LEFT_BRACE,
      RIGHT_BRACE,
      COMMA,
      DOT,
      MINUS,
      PLUS,
      PERCENT,
      SEMICOLON,
      SLASH,
      STAR,
      BANG,
      BANG_EQUAL,
      EQUAL,
      EQUAL_EQUAL,
      GREATER,
      GREATER_EQUAL,
      LESS,
      LESS_EQUAL,
      AND_AND,
      OR_OR,
      QUESTION,
      COLON,
      IDENTIFIER,
      STRING,
      NUMBER,
      LET,
      IF,
      ELSE,
      WHILE,
      FOR,
      TRUE,
      FALSE,
      NIL,
      SETBLOCK,
      EOF;
   }

   static final class Unary implements StructureFromCode.Expr {
      final StructureFromCode.Token op;
      final StructureFromCode.Expr right;

      Unary(StructureFromCode.Token o, StructureFromCode.Expr r) {
         this.op = o;
         this.right = r;
      }

      @Override
      public <R> R accept(StructureFromCode.ExprVisitor<R> v) {
         return v.visitUnary(this);
      }
   }

   static final class Var implements StructureFromCode.Stmt {
      final StructureFromCode.Token name;
      final StructureFromCode.Expr initializer;

      Var(StructureFromCode.Token n, StructureFromCode.Expr init) {
         this.name = n;
         this.initializer = init;
      }

      @Override
      public void accept(StructureFromCode.StmtVisitor v) {
         v.visitVarStmt(this);
      }
   }

   static final class Variable implements StructureFromCode.Expr {
      final StructureFromCode.Token name;

      Variable(StructureFromCode.Token n) {
         this.name = n;
      }

      @Override
      public <R> R accept(StructureFromCode.ExprVisitor<R> v) {
         return v.visitVariable(this);
      }
   }

   static final class While implements StructureFromCode.Stmt {
      final StructureFromCode.Expr condition;
      final StructureFromCode.Stmt body;

      While(StructureFromCode.Expr c, StructureFromCode.Stmt b) {
         this.condition = c;
         this.body = b;
      }

      @Override
      public void accept(StructureFromCode.StmtVisitor v) {
         v.visitWhileStmt(this);
      }
   }
}
