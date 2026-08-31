# Kotlin 代码规范与命名规范（官方基准）

> 依据 Kotlin 官方《Coding conventions》（kotlinlang.org，2026-08-12 版）与 Android 官方《Kotlin style guide》（developer.android.com，2023-09 版）整理，结合本项目 Fast16 约定（AGENTS.md §6）与 `gradle.properties` 的 `kotlin.code.style=official`。写/Review 任何 Kotlin 代码前必读。

## 0. 三条铁律（先记住）

1. **命名一眼分类型**：常量 `UPPER_SNAKE_CASE`，其余属性/函数 `camelCase`，类/对象/接口 `PascalCase`；**禁用**匈牙利前缀（`mName`、`s_name`、`kName`）——后备属性除外（`_` 前缀）。
2. **格式机器可校验**：4 空格缩进、K&R 大括号、100 列上限、无分号、import 按 ASCII 排序且**禁通配符**。
3. **一致性靠 IDE + 官方 style**：本项目已设 `kotlin.code.style=official`，风格以 Android Studio 自动格式化与 `Incorrect formatting` 检查为准，不手工对齐。

---

## 1. 命名规范

### 1.1 包名
- 全小写、单词直接连接、**不用下划线**：`com.ly.fast16.core.device`、`com.ly.fast16.feature.home.ui`。

### 1.2 类型（类 / 接口 / 对象 / 枚举）
- `PascalCase`，名词或名词短语：`MealPlan`、`ImmutableList`。
- 测试类：被测类名开头 + `Test` 结尾：`MealPlanTest`。
- 类型变量：单个大写字母（可跟数字）或「类名 + T」：`T`、`E`、`RequestT`。

### 1.3 函数
- `camelCase`，动词或动词短语，并体现是否修改对象：`sort()`（原地）vs `sorted()`（返回副本）。
- **例外 1（工厂函数）**：可与抽象返回类型同名：`fun Foo(): Foo = FooImpl()`。
- **例外 2（Composable）**：`@Composable` 且返回 `Unit` 的函数用 `PascalCase` 名词：
  ```kotlin
  @Composable fun TabHeader() { /*...*/ }
  ```
- **测试函数**：可用反引号含空格（官方注明该形式仅 Android API 30+ 支持，本项目 `minSdk 24` **不要用**），推荐下划线分词：`ensureEverythingWorks_onAndroid()`。

### 1.4 属性与常量（最易混，重点）
| 类别 | 判定条件 | 命名 |
|------|----------|------|
| 常量 | `val` + **无自定义 getter** + 内容深层不可变 + 无副作用 | `UPPER_SNAKE_CASE`（顶层 / `object` 内，标量必加 `const`） |
| 非常量属性/局部变量/参数 | 其余全部 | `camelCase` |
| 持有单例引用的属性 | 引用 `object` 实例 | 可与该 `object` 同名风格（`PersonComparator`） |
| 枚举常量 | 枚举成员 | 本项目统一 `PascalCase`（如 `Pending`、`Confirmed`；官方允许 SCREAMING_SNAKE，选一即可） |

```kotlin
const val MAX_COUNT = 8                // 标量常量：const + UPPER_SNAKE_CASE
val USER_NAME_FIELD = "UserName"       // 深层不可变 val
val mutableCollection: MutableSet<String> = HashSet()   // 可变 → camelCase

class Widget {
    // 注意：class 内即使深层不可变也按非常量命名（官方明确）
    val defaultSize = 8
}
```

### 1.5 后备属性（Backing property）
同一概念分公开 API 与私有实现时，私有属性加 `_` 前缀：

```kotlin
class C {
    private val _elementList = mutableListOf<Element>()
    val elementList: List<Element>
        get() = _elementList
}
```

### 1.6 缩写
- 两个字母：全大写：`IOStream`。
- 三个及以上：只大写首字母：`XmlFormatter`、`HttpInputStream`。
- camelCase 转换时原大小写几乎被忽略：`XML Http Request` → `XmlHttpRequest`（不是 `XMLHTTPRequest`）；`supports IPv6 on iOS` → `supportsIpv6OnIos`。

### 1.7 命名质量
- 类名用名词，方法名用动词；避免无意义词：`Manager`、`Wrapper`、`Util` 一律不用（文件名同理）。

---

## 2. 源文件组织

### 2.1 文件名
- 只含单个类/接口 → 文件名 = 类名 + `.kt`（区分大小写）。
- 含多个顶层声明 → 描述性 `PascalCase`：`ProcessDeclarations.kt`；**避免 `Util`**。
- 平台 source set 文件带后缀：`Platform.jvm.kt` / `Platform.android.kt`；公共 set 不带（本项目单模块，仅作扩展提示）。

### 2.2 文件结构（按序 + 空行分隔）
```
1. 版权/许可头（可选，用 /* */ 多行注释，勿用 KDoc 或 //）
2. 文件级注解
3. package 语句
4. import 语句
5. 顶层声明
```

### 2.3 import（项目红线，见 AGENTS.md §6）
- 按 ASCII 排序、单列；**禁通配符导入**（`import foo.*`）。
- **先 import 再使用，禁 FQN**：`com.ly.fast16.feature.home.ui.HomeViewModel()`、`androidx.compose.ui.graphics.Color.Black`、`org.koin.androidx.compose.koinViewModel()` 一律改 import 后短名；KDoc 链接 `[ClassName]` 同样用短名。

### 2.4 类内布局
顺序：① 属性声明与初始化块 → ② 次级构造函数 → ③ 方法声明 → ④ 伴生对象。
- 方法按**逻辑顺序**排列，不按字母/可见性排序；重载方法**必须相邻**。
- 嵌套类放在使用它的代码旁；仅供外部使用则放伴生对象之后。
- 实现接口时，成员顺序尽量与接口声明一致（可穿插必要私有辅助方法）。
- 语义紧密相关的多个声明可放同一文件，但控制在几百行内。

### 2.5 扩展函数放置
- 对类所有客户端都相关 → 与该类同文件。
- 仅特定客户端使用 → 放该客户端代码旁。
- **不要**为集中某类全部扩展而单独建文件。

---

## 3. 格式化

### 3.1 缩进与大括号
- 源文件一律 **UTF-8** 编码；4 空格缩进，**禁 Tab**；左花括号行尾（K&R），右花括号与构造开始对齐。
- 字符串/字符优先用转义序列（`\b`、`\n`、`\r`、`\t`、`\'`、`\"`、`\\`、`\$`）而非 `\uXXXX`；非 ASCII 优先写实际字符（如 `∞`）。
- **多行条件必须使用花括号**；条件后续行缩进 4 空格，右括号与左花括号同行。
- `when` 分支超过一行时，用空行与相邻分支分隔。
- 单行 `if`（无 `else` 或单分支）与短 `when` 分支可省花括号：
  ```kotlin
  if (string.isEmpty()) return
  val result = if (string.isEmpty()) DEFAULT_VALUE else string
  ```

### 3.2 换行（100 列上限）
- 二元运算符/infix 处换行 → 换行符**在运算符后**；`.`、`?.`、`::` 处换行 → 换行符**在其前**。
- 方法名/构造函数名**始终紧贴**左括号 `(`；逗号紧贴其前标记；lambda 箭头 `->` 紧贴参数列表。
- 例外：无法缩短的 URL、`package`/`import` 语句、可粘贴 shell 的命令行注释。

### 3.3 函数签名换行
参数每行一个（+4 缩进），右括号与返回类型独占一行（无额外缩进）：

```kotlin
fun <T> Iterable<T>.joinToString(
    separator: CharSequence = ", ",
    prefix: CharSequence = "",
    postfix: CharSequence = "",
): String { ... }
```

### 3.4 空白
- **垂直**：连续成员之间空一行（连续两个属性之间可选）；用于逻辑分组。
- **水平**（关键）：
  | 场景 | 规则 |
  |------|------|
  | 保留字与 `(` 之间 | 加空格：`if (`、`when (`、`for (` |
  | `else`/`catch` 与前 `}` | 加空格：`} else {` |
  | 二元运算符两侧 | 加空格：`val two = 1 + 1`（含 `->`；`0..i` 例外） |
  | 主构造/方法声明/调用左括号前 | **不加**：`fun foo(x: Int)` |
  | `(`、`[` 后 / `]`、`)` 前 | 不加空格 |
  | `.`、`?.`、`::` 周围 / 类型参数尖括号内 / 可空 `?` 前 | 不加空格 |
  | 冒号 | 声明与类型之间不加；父类型/委托/`object` 关键字后的冒号前**加**；冒号后永远加 |
  | `//` 注释 | `//` 后加空格 |
  | 水平对齐 | 禁止（避免对齐赋值号/冒号） |

### 3.5 修饰符顺序（官方固定）
```
public / protected / private / internal
expect / actual
final / open / abstract / sealed / const
external
override
lateinit
tailrec
vararg
suspend
inner
enum / annotation / fun
companion
inline / value
infix
operator
data
```
- 注解放在所有修饰符之前；库之外可省略冗余的 `public`。
- 注解排版：多参数注解单独一行、与声明同缩进；无参注解可与声明同行；文件注解位于文件注释之后、`package` 之前，并与 `package` 空行分隔。

### 3.6 链式调用与尾随逗号
```kotlin
val anchor = owner
    ?.firstChild!!
    .siblings(forward = true)
    .dropWhile { it is PsiComment || it is PsiWhiteSpace }
```
- 尾随逗号：**鼓励在声明处使用**（利于 diff 与增删元素），调用处自行决定：
  ```kotlin
  class Person(
      val firstName: String,
      val lastName: String,
      val age: Int, // trailing comma
  )
  ```

### 3.7 单表达式函数
- 单一表达式优先用表达式体：`fun foo() = 1`；首行放不下时 `=` 留在第一行，表达式体缩进 4 空格。

### 3.8 属性、枚举与 Lambda 格式化
- **属性**：简单只读属性可单行：`val isEmpty: Boolean get() = size == 0`；复杂 `get`/`set` 每行独占（+4 缩进）；初始化器过长时在 `=` 后换行并缩进 4 空格。
- **枚举**：无成员函数时可单行：`enum class Answer { YES, NO, MAYBE }`。
- **Lambda**：花括号与 `->` 两侧加空格；**单个 Lambda 尽量放在括号外**；lambda 标签与左花括号之间不加空格；多行参数名与箭头同行。
- **方法调用**：长参数列表在左括号后换行，参数缩进 4 空格；命名参数 `=` 两侧加空格。

---

## 4. KDoc 文档注释

### 4.1 格式
```kotlin
/** 短注释可单行。 */
/**
 * 多行：`/**` 独占一行，后续每行以 ` * ` 开头。
 *
 * 段落之间用空白的 ` *` 行分隔。
 */
```

### 4.2 摘要与块标记
- 每段 KDoc 以简短摘要开头（名词/动词短语），首字母大写并加标点；**不以** "A `Foo` is a..." 或 "This method returns..." 开头。
- 块标记顺序固定：`@constructor` → `@receiver` → `@param` → `@property` → `@return` → `@throws` → `@see`；续行缩进 4 空格。
- **一般避免 `@param`/`@return`**：把描述融入正文并链接参数，仅长篇说明时才用。

### 4.3 必写范围
- 每个 `public` 类型及其 `public`/`protected` 成员应有 KDoc。
- 例外：① 不言自明（`getFoo`/`foo`，但 `canonicalName` 这类术语不能省）；② override 方法（替换超类型实现时）。

---

## 5. 避免冗余与惯用写法

- **冗余**：`Unit` 返回类型省略；分号能省则省；字符串模板简单变量直接 `$name`，复杂表达式才 `${...}`。
- **不可变性**：优先 `val` 与不可变集合（`List`/`Set`/`Map`），创建时用返回不可变类型的工厂函数。
- **默认参数**优先于重载函数；重复的函数类型用 `typealias`，名称冲突优先 `import ... as ...`。
- **命名参数**：多个同基本类型参数或 `Boolean` 参数时必须用命名参数。
- **条件表达式化**：
  ```kotlin
  return if (x) foo() else bar()
  return when (x) { 0 -> "zero" else -> "nonzero" }
  ```
  二元条件用 `if`，三个及以上选项用 `when`（Kotlin 2.2 可用 when 守卫条件：`is Status.Ok if (status.info.isEmpty()) -> "no information"`）；可空 `Boolean` 显式比较 `if (value == true)`。
- **循环**：优先 `filter`/`map` 等高阶函数；`forEach` 仅当接收者可空或作为长链一部分时用。
- **范围循环**：开区间用 `0..<n`，不用 `0..n - 1`。
- **多行字符串**：用 `trimIndent()` / `trimMargin()` 保持缩进。
- **函数 vs 属性**：无参、不抛异常、计算廉价（可缓存）、状态不变时结果相同 → 用属性。
- **作用域函数**：`let`/`run`/`with`/`apply`/`also` 按官方专门文档选型，避免嵌套滥用。
- **lambda**：短且不嵌套用 `it`；嵌套 lambda 必须显式声明参数；避免多重标签 return。
- **扩展函数/中缀**：多使用扩展函数并限制可见性（局部/private）；中缀仅当两对象角色对等（`and`/`to`/`zip`），不用于会修改接收者的方法。
- **平台类型**：public 函数/属性的平台类型表达式必须显式声明 Kotlin 类型；局部变量可省略。
- **库级提示**（本项目非库，可参考）：公共成员显式指定返回类型与可见性，避免意外暴露 API。

---

## 6. 项目红线与落地

### 6.1 与 AGENTS.md §6 联动（本项目特化）
- **import 优先、禁 FQN**（§6 红线，此处细化到 KDoc 链接），见本文 §2.3。
- **MVI 三件套命名**：Screen 层 `Intent → Reducer → State`，Redux 式纯函数；命名如 `HomeIntent` / `HomeUiState` / `homeReducer`；ViewModel 暴露 `StateFlow`。
- **feature-first 包结构**（`feature → domain ← data`）：`feature/<模块>/ui|data`、`domain/`、`core/`；feature 间禁止横向依赖。
- **Room 命名**：实体用 `@Entity` 的 `data class`（PascalCase），DAO 接口 `<Entity>Dao`；枚举存 `Int` + TypeConverter。
- 与其余规范联动：Compose UI 遵循 [`compose-performance.md`](compose-performance.md)、并发遵循 [`coroutines-best-practices.md`](coroutines-best-practices.md)、测试遵循 [`testing-best-practices.md`](testing-best-practices.md)。

### 6.2 工具落地
- **IDE**：`Settings/Preferences | Editor | Code Style | Kotlin` → `Set from...` → **Kotlin style guide**；`Editor | Inspections | General` 启用 **Incorrect formatting**（命名等检查默认已开）。
- **Gradle**：本项目 `gradle.properties` 已设 `kotlin.code.style=official`（与官方一致，勿改回 obsolete）。
- **未引入 ktlint/detekt**：统一靠 Android Studio 内置格式化 + `./gradlew :app:lintDebug`；若后续引入，规则集必须与本文（即官方）对齐。

### 6.3 验证
```bash
./gradlew :app:lintDebug   # lint（含部分格式/命名检查）
```
新增/修改 Kotlin 文件后：先 IDE 自动格式化（`Reformat Code`），再按本文 §1–§5 逐节自查。
