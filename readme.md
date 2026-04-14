# GitHub 文件管理器

一个基于 Jetpack Compose 的 Android 应用，用于浏览和管理 GitHub 仓库文件。

## 功能特性

- GitHub Personal Access Token 登录认证
- 查看用户的所有仓库列表
- 浏览仓库目录结构（文件树）
- 查看和编辑文件内容
- 创建、更新、删除文件
- 文件重命名和移动
- Material Design 3 UI

## 技术栈

- **UI**: Jetpack Compose + Material 3
- **网络**: Retrofit 2 + OkHttp
- **架构**: MVVM + Repository
- **异步**: Kotlin Coroutines + Flow
- **编译**: Gradle Kotlin DSL

## 项目结构

```
app/src/main/java/com/github/filemanager/
├── MainActivity.kt              # 主入口
├── data/
│   ├── api/                     # GitHub API 接口
│   ├── model/                   # 数据模型
│   ├── network/                 # 网络配置
│   └── repository/              # 数据仓库
└── ui/
    ├── GitHubFileManagerApp.kt  # 主导航
    ├── screens/                 # UI 页面
    ├── theme/                   # 主题配置
    └── viewmodel/               # ViewModel
```

## 配置

### 1. 生成 GitHub Token

1. 登录 GitHub → Settings → Developer settings
2. Personal access tokens → Generate new token
3. 勾选 `repo` 权限（完整仓库访问）

### 2. 构建项目

```bash
./gradlew assembleDebug
```

### 3. 安装 APK

```bash
./gradlew installDebug
```

## 使用说明

1. 启动应用后输入 GitHub Personal Access Token
2. 点击"登录"按钮进行认证
3. 从仓库列表中选择要浏览的仓库
4. 点击文件夹进入子目录，点击文件查看/编辑内容
5. 长按文件可进行重命名、移动或删除操作

## 注意事项

- Token 需要具有 `repo` 权限
- 文件内容通过 Base64 编解码
- 更新或删除文件需要提供文件 SHA 值
- 大文件（>100MB）可能需要 Git LFS

## License

MIT
