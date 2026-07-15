package com.rethinkingstudio.clawlink.ui.screens.legal.models

import com.rethinkingstudio.clawlink.core.network.dto.LegalConsentRequest

const val LEGAL_DOCUMENT_VERSION = LegalConsentRequest.CURRENT_TERMS_VERSION

enum class LegalDocumentType {
    TERMS,
    PRIVACY
}

data class LegalDocumentSection(
    val chineseTitle: String,
    val englishTitle: String,
    val chineseParagraphs: List<String>,
    val englishParagraphs: List<String>
)

data class LegalDocument(
    val chineseTitle: String,
    val englishTitle: String,
    val chineseEyebrow: String,
    val englishEyebrow: String,
    val chineseSummary: String,
    val englishSummary: String,
    val sections: List<LegalDocumentSection>,
    val version: String = LEGAL_DOCUMENT_VERSION
)

fun legalDocument(type: LegalDocumentType): LegalDocument = when (type) {
    LegalDocumentType.TERMS -> termsDocument
    LegalDocumentType.PRIVACY -> privacyDocument
}

private val termsDocument = LegalDocument(
    chineseTitle = "ClawLink 用户协议",
    englishTitle = "ClawLink User Agreement",
    chineseEyebrow = "服务协议",
    englishEyebrow = "SERVICE AGREEMENT",
    chineseSummary = "使用 ClawLink 前，请阅读并理解本协议。点击同意并继续使用，即表示您接受本协议当前版本。",
    englishSummary = "Please read and understand this agreement before using ClawLink. By agreeing and continuing, you accept the current version.",
    sections = listOf(
        section(
            "一、服务内容",
            "1. Service",
            listOf(
                "ClawLink 用于连接您授权的 Relay、ClawConnect、OpenClaw 或 Hermes 环境，并提供登录、配对、聊天、文件、任务及网关管理等功能。",
                "具体功能可能随版本更新而调整；涉及重要权益变化时，我们会通过适当方式提示。"
            ),
            listOf(
                "ClawLink connects Relay, ClawConnect, OpenClaw, or Hermes environments that you authorize, and provides sign-in, pairing, chat, file, task, and gateway management features.",
                "Features may change with product updates. We will provide appropriate notice when a change materially affects your rights."
            )
        ),
        section(
            "二、账号与登录",
            "2. Account and sign-in",
            listOf(
                "您可以使用邮箱创建账号。注册时需要完成邮箱验证码校验，验证通过后由服务端建立 ClawLink 登录状态。",
                "请妥善保管登录设备、邮箱及密码，不得出租、转让账号或协助他人绕过安全控制。"
            ),
            listOf(
                "You may create an account with email. Registration requires email verification, after which the server establishes a ClawLink session.",
                "Protect your signed-in devices, email, and password. Do not rent or transfer your account or help others bypass security controls."
            )
        ),
        section(
            "三、使用规则",
            "3. Acceptable use",
            listOf(
                "您应确保连接的网关、模型、文件和自动化任务均已获得合法授权，并对通过自身账号发起的操作负责。",
                "不得利用本服务侵害他人权益、破坏系统安全、传播违法内容或实施未经授权的访问。"
            ),
            listOf(
                "You must have lawful authorization for connected gateways, models, files, and automated tasks, and are responsible for actions initiated through your account.",
                "Do not use the service to infringe rights, compromise system security, distribute unlawful content, or obtain unauthorized access."
            )
        ),
        section(
            "四、服务可用性",
            "4. Availability",
            listOf("网络、Relay、第三方模型或您自有网关的异常可能影响服务。我们会在合理范围内维护服务，但不承诺所有外部依赖始终可用。"),
            listOf("Network conditions, Relay, third-party models, or your own gateways may affect the service. We maintain ClawLink within reasonable limits but cannot guarantee every external dependency.")
        ),
        section(
            "五、退出与注销",
            "5. Sign-out and deletion",
            listOf("您可以在设置的账号管理中退出登录或申请注销账号。账号注销后，服务端将按产品规则删除或匿名化相关账号数据；依法需要保留的记录除外。"),
            listOf("You may sign out or request account deletion in Settings. After deletion, related account data is deleted or anonymized under product rules, except records that must be retained by law.")
        ),
        section(
            "六、协议与联系",
            "6. Agreement and contact",
            listOf("本协议的运营主体以应用资料页公示信息为准。如有疑问，可通过应用资料页展示的主体联系方式提出。"),
            listOf("The operator is identified on the app information page. Questions may be submitted through the contact details published there.")
        )
    )
)

private val privacyDocument = LegalDocument(
    chineseTitle = "ClawLink 隐私政策",
    englishTitle = "ClawLink Privacy Policy",
    chineseEyebrow = "隐私保护",
    englishEyebrow = "PRIVACY",
    chineseSummary = "我们遵循最小必要原则处理个人信息，仅用于提供您主动使用的 ClawLink 功能。",
    englishSummary = "We process only the personal information necessary to provide the ClawLink features you choose to use.",
    sections = listOf(
        section(
            "一、登录与账号信息",
            "1. Sign-in and account data",
            listOf(
                "使用邮箱注册和登录时，我们处理您主动提供的姓名、邮箱、密码摘要及邮箱验证码状态，用于创建账号、验证身份和保持登录状态。",
                "我们还会处理稳定设备标识、Android 平台和应用版本，用于设备与会话管理、兼容性判断及账号安全。",
                "密码不会以明文形式保存；邮箱验证码仅在完成验证和安全控制所需的期限内处理。"
            ),
            listOf(
                "For email registration and sign-in, we process the name and email you provide, a password hash, and email verification status to create your account, verify your identity, and maintain your session.",
                "We also process a stable device identifier, the Android platform, and app version for device and session management, compatibility, and account security.",
                "Passwords are not stored in plaintext. Email verification codes are processed only as long as needed for verification and security controls."
            )
        ),
        section(
            "二、功能数据",
            "2. Feature data",
            listOf(
                "当您主动使用相应功能时，我们会处理网关配对与状态、聊天消息、文件与语音、模型与技能配置、任务和备份记录，以完成您请求的操作。",
                "相册、相机、麦克风和文件能力仅在您主动触发相关功能时使用，并按系统要求请求授权。"
            ),
            listOf(
                "When you use related features, we process gateway pairing and status, chat messages, files and voice, model and skill settings, tasks, and backup records to perform your requests.",
                "Photos, camera, microphone, and file access are used only when you initiate the related feature and after system permission is requested."
            )
        ),
        section(
            "三、使用目的",
            "3. Purposes",
            listOf("上述信息用于账号登录、设备与会话管理、连接您授权的网关、传输您请求的内容、故障排查、安全审计及防止滥用，不用于出售个人信息。"),
            listOf("We use this information for sign-in, device and session management, authorized gateway connections, requested content transfer, troubleshooting, security auditing, and abuse prevention. We do not sell personal information.")
        ),
        section(
            "四、共享与委托处理",
            "4. Sharing and processing",
            listOf("为发送邮箱验证码，必要的邮箱地址和验证码内容会经邮件投递服务处理；为执行您主动发起的操作，数据可能传输至您选择或控制的 Relay、网关及模型服务。除实现功能、履行法定义务或取得另行授权外，不向无关第三方提供个人信息。"),
            listOf("To send verification emails, the required email address and verification code are processed by the email delivery service. Data may also be sent to Relay, gateway, and model services you select or control to perform your requests. We do not provide personal information to unrelated third parties except to deliver features, meet legal duties, or with separate authorization.")
        ),
        section(
            "五、保存与安全",
            "5. Retention and security",
            listOf("数据在实现功能所需期限内保存。我们采用访问控制、传输加密、密钥隔离和日志脱敏等措施保护数据，但互联网服务无法保证绝对安全。"),
            listOf("Data is retained only as long as needed for the service. We use access controls, encrypted transport, secret isolation, and log redaction, though no internet service can guarantee absolute security.")
        ),
        section(
            "六、您的权利",
            "6. Your rights",
            listOf("您可以在应用设置中查看或修改部分账号信息、退出登录、管理系统授权并申请注销账号。系统权限也可在设备设置中调整。"),
            listOf("In app Settings, you may review or change certain account details, sign out, manage permissions, and request account deletion. System permissions can also be changed in device settings.")
        ),
        section(
            "七、政策更新与联系",
            "7. Updates and contact",
            listOf("政策发生重要变化时，我们会更新版本并在再次登录或使用相关能力前提示。运营主体及联系方式以应用资料页公示信息为准。"),
            listOf("When this policy changes materially, we will update its version and provide notice before the next sign-in or use of affected features. Operator and contact details are published on the app information page.")
        )
    )
)

private fun section(
    chineseTitle: String,
    englishTitle: String,
    chineseParagraphs: List<String>,
    englishParagraphs: List<String>
) = LegalDocumentSection(chineseTitle, englishTitle, chineseParagraphs, englishParagraphs)
