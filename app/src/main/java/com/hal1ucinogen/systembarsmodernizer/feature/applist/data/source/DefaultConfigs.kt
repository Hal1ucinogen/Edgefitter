package com.hal1ucinogen.systembarsmodernizer.feature.applist.data.source

import android.graphics.Color
import com.hal1ucinogen.systembarsmodernizer.bean.AppConfig
import com.hal1ucinogen.systembarsmodernizer.bean.ExtraAction
import com.hal1ucinogen.systembarsmodernizer.bean.GeneralConfig
import com.hal1ucinogen.systembarsmodernizer.bean.InsetEdge
import com.hal1ucinogen.systembarsmodernizer.bean.PageConfig
import com.hal1ucinogen.systembarsmodernizer.bean.SpacingType
import com.hal1ucinogen.systembarsmodernizer.bean.ViewAction
import com.hal1ucinogen.systembarsmodernizer.bean.VisibilityMode

object DefaultConfigs {
    val configs = listOf(
        // Spotify
        AppConfig(
            "com.spotify.music",
            1,
            mapOf(
                "com.spotify.nowplaying.musicinstallation.NowPlayingActivity" to PageConfig(
                    edgeToEdge = false,
                    clearTranslucent = true
                )
            )
        ),
        // 闲鱼
        AppConfig(
            "com.taobao.idlefish",
            1,
            mapOf(
                "com.taobao.idlefish.webview.WebHybridActivity" to PageConfig(
                    edgeToEdge = true,
                    extraActions = listOf(
                        ExtraAction(
                            viewId = "decor",
                            isGroup = true,
                            self = false,
                            childIndex = 0,
                            action = ViewAction.Inset(
                                spacingType = SpacingType.MARGIN,
                                edge = InsetEdge.BOTTOM,
                                useSystemInsets = false,
                                customInset = 0
                            )
                        )
                    )
                ),
                "com.taobao.idlefish.search_implement.SearchResultActivity" to PageConfig(
                    edgeToEdge = true,
                    extraActions = listOf(
                        ExtraAction(
                            viewId = "decor",
                            isGroup = true,
                            self = false,
                            childIndex = 0,
                            action = ViewAction.Inset(
                                spacingType = SpacingType.MARGIN,
                                edge = InsetEdge.BOTTOM,
                                useSystemInsets = false,
                                customInset = 0
                            )
                        )
                    )
                ),
                "com.idlefish.flutterbridge.flutterboost.boost.FishFlutterBoostActivity" to PageConfig(
                    edgeToEdge = true,
                    extraActions = listOf(
                        ExtraAction(
                            viewId = "decor",
                            isGroup = true,
                            self = false,
                            childIndex = 0,
                            routes = listOf("x_chat", "rate_detail", "order_detail_new"),
                            isRouteExclusive = true,
                            routeKey = "url",
                            action = ViewAction.Inset(
                                spacingType = SpacingType.MARGIN,
                                edge = InsetEdge.BOTTOM,
                                useSystemInsets = false,
                                customInset = 0
                            )
                        )
                    )
                )
            ),
            GeneralConfig(
                PageConfig(edgeToEdge = true),
                exclusive = listOf(
                    "com.taobao.idlefish.maincontainer.activity.MainActivity",
                    "com.taobao.idlefish.detail.DetailActivity"
                )
            )
        ),
        // 京东
        AppConfig(
            "com.jingdong.app.mall",
            1,
            mapOf(
                /*"com.jingdong.app.mall.MainFrameActivity" to PageConfig(
                    edgeToEdge = false,
                    navigationColor = Color.TRANSPARENT,
                    windowBackgroundColor = Color.WHITE,
                    extraActions = listOf(
//                        ExtraAction(
//                            viewId = "navigation_fragment",
//                            isGroup = true,
//                            self = true,
//                            action = ViewAction.Inset(
//                                spacingType = SpacingType.MARGIN,
//                                edge = InsetEdge.BOTTOM,
//                                useSystemInsets = true
//                            )
//                        ),
                        ExtraAction(
                            viewId = "bottomMenu_Code",
                            isGroup = true,
                            self = false,
                            childIndex = 1,
                            delay = 1000L,
                            action = ViewAction.Visibility(
                                mode = VisibilityMode.GONE
                            )
                        )
                    )
                )*/
            ),
            general = GeneralConfig(
                PageConfig(edgeToEdge = true),
                exclusive = listOf(
                    "com.jingdong.app.mall.MainFrameActivity",
                    "com.jd.lib.settlement.fillorder.activity.PopupNewFillOrderActivity",
                    "com.jd.lib.productdetail.ProductDetailPopActivity",
                    "com.jd.lib.productdetail.ProductDetailActivity"
                )
            )
        ),
        // SBM - Checker
        AppConfig(
            "com.hal1ucinogen.sbmchecker",
            1,
            mapOf("com.hal1ucinogen.sbmchecker.MainActivity" to PageConfig(edgeToEdge = true))
        ),
        // 淘宝
        AppConfig(
            "com.taobao.taobao",
            1,
            mapOf(
                "com.taobao.tao.welcome.Welcome" to PageConfig(
                    edgeToEdge = false,
                    navigationColor = Color.TRANSPARENT,
                    windowBackgroundColor = Color.WHITE,
                    extraActions = listOf(
                        ExtraAction(
                            viewId = "tabs",
                            isGroup = true,
                            self = false,
                            childIndex = 1,
                            action = ViewAction.Visibility(
                                mode = VisibilityMode.GONE
                            )
                        )
                    )
                ),
                "com.taobao.themis.container.app.TMSActivity" to PageConfig(
                    edgeToEdge = true,
                    extraActions = listOf(
                        ExtraAction(
                            viewId = "decor",
                            isGroup = true,
                            self = false,
                            childIndex = 0,
                            routes = listOf("taopiaopiao", "outfliggys"),
                            isRouteExclusive = true,
                            routeKey = "extra_data_key",
                            action = ViewAction.Inset(
                                spacingType = SpacingType.MARGIN,
                                edge = InsetEdge.BOTTOM,
                                useSystemInsets = false,
                                customInset = 0
                            )
                        )
                    )
                ),
                "com.taobao.android.detail2.core.framework.NewDetailActivity" to PageConfig(
                    edgeToEdge = true,
                    extraActions = listOf(
                        ExtraAction(
                            viewId = "decor",
                            isGroup = true,
                            self = false,
                            childIndex = 0,
                            action = ViewAction.Inset(
                                spacingType = SpacingType.MARGIN,
                                edge = InsetEdge.BOTTOM,
                                useSystemInsets = false,
                                customInset = 0
                            )
                        )
                    )
                ),
                "com.taobao.weex.weexv2.page.WeexV2Activity" to PageConfig(
                    edgeToEdge = true,
                    extraActions = listOf(
                        ExtraAction(
                            viewId = "decor",
                            isGroup = true,
                            self = false,
                            childIndex = 0,
                            action = ViewAction.Inset(
                                spacingType = SpacingType.MARGIN,
                                edge = InsetEdge.BOTTOM,
                                useSystemInsets = false,
                                customInset = 0
                            )
                        )
                    )
                )
            ),
            general = GeneralConfig(
                PageConfig(edgeToEdge = true),
                exclusive = listOf(
                    "com.taobao.android.detail.alittdetail.TTDetailActivity",
                    "com.taobao.android.purchase.aura.TBBuyActivity"
                )
            )
        ),
        // 电笠
        AppConfig(
            "media.dl",
            1,
            emptyMap(),
            general = GeneralConfig(PageConfig(edgeToEdge = true))
        ),
        // 小黑盒
        AppConfig(
            "com.max.xiaoheihe", 1,
            mapOf(
                "com.max.xiaoheihe.MainActivity" to PageConfig(
                    edgeToEdge = false,
                    uiModeWBC = Pair(Color.WHITE, Color.parseColor("#111111"))
                ),
                "com.max.xiaoheihe.module.bbs.ChannelsDetailActivity" to PageConfig(edgeToEdge = true),
                "com.max.xiaoheihe.module.webview.WebActionActivity" to PageConfig(edgeToEdge = true),
            ),
        ),
        // DouBan
        AppConfig(
            "com.douban.frodo",
            1,
            mapOf(
                "com.douban.frodo.baseproject.image.ImageActivity" to PageConfig(edgeToEdge = true),
                "com.douban.frodo.baseproject.image.SociableImageActivity" to PageConfig(
                    edgeToEdge = true,
                    extraActions = listOf(
                        ExtraAction(
                            viewId = "social_action_bar",
                            isGroup = true,
                            self = true,
                            action = ViewAction.Inset(
                                spacingType = SpacingType.MARGIN,
                                edge = InsetEdge.BOTTOM,
                                useSystemInsets = true
                            )
                        )
                    )
                ),
                "com.douban.frodo.profile.activity.NewUserProfileActivity" to PageConfig(edgeToEdge = true)
            )
        ),
        // 饿了么
        AppConfig(
            "me.ele",
            1,
            mapOf(
                "me.ele.pha.shell.ui.ElePhaActivity" to PageConfig(edgeToEdge = true),
                "me.ele.muise.page.WeexPageActivity" to PageConfig(edgeToEdge = true),
                "me.ele.orderdetail.ui.lmagex.WMOrderDetailActivity" to PageConfig(edgeToEdge = true),
                "me.ele.newretail.emagex.activity.EMagexOrderDetailActivity" to PageConfig(
                    edgeToEdge = true
                ),
                "com.alibaba.triver.container.TriverMainActivity" to PageConfig(edgeToEdge = true),
                "me.ele.component.webcontainer.view.AppUCWebActivity" to PageConfig(
                    edgeToEdge = true,
                    extraActions = listOf(
                        ExtraAction(
                            viewId = "comp_uc_container",
                            isGroup = true,
                            self = false,
                            childIndex = 0,
                            action = ViewAction.Inset(
                                spacingType = SpacingType.PADDING,
                                edge = InsetEdge.BOTTOM,
                                useSystemInsets = false,
                                customInset = 0
                            )
                        )
                    )
                )
            )
        ),
        // UnionPay
        AppConfig(
            "com.unionpay",
            1,
            mapOf(
                "com.unionpay.activity.message.UPActivityMesssage" to PageConfig(edgeToEdge = true),
                "com.unionpay.activity.react.UPActivityReactNative" to PageConfig(edgeToEdge = true),
            )
        ),
        // Bili
        AppConfig(
            "com.bilibili.app.in",
            1,
            mapOf("tv.danmaku.bili.ui.webview.MWebActivity" to PageConfig(edgeToEdge = true))
        ),
        // 支付宝
        AppConfig(
            "com.eg.android.AlipayGphone",
            1,
            mapOf(
                "com.alipay.mobile.nebulax.xriver.activity.XRiverActivity" to PageConfig(
                    edgeToEdge = true,
                    extraActions = listOf(
                        ExtraAction(
                            viewId = "nebulax_root_view",
                            isGroup = false,
                            action = ViewAction.Inset(
                                spacingType = SpacingType.PADDING,
                                edge = InsetEdge.BOTTOM,
                                useSystemInsets = false,
                                customInset = 0
                            )
                        )
                    )
                ),
                "com.alipay.android.phone.msgboxapp.ui.activity.*" to PageConfig(
                    edgeToEdge = true,
                    extraActions = listOf(
                        ExtraAction(
                            viewId = "v_navbar_placeholder",
                            action = ViewAction.Visibility(
                                mode = VisibilityMode.GONE
                            )
                        )
                    )
                ),
                "com.alipay.mobile.antcardsdk.cardapp.CSPushActivity" to PageConfig(
                    edgeToEdge = true,
                    extraActions = listOf(
                        ExtraAction(
                            viewId = "decor",
                            isGroup = true,
                            self = false,
                            childIndex = 0,
                            action = ViewAction.Inset(
                                spacingType = SpacingType.PADDING,
                                edge = InsetEdge.BOTTOM,
                                useSystemInsets = false,
                                customInset = 0
                            )
                        )
                    )
                ),
                "com.alipay.mobile.socialcontactsdk.contact.ui.ContactMainPageActivity" to PageConfig(
                    edgeToEdge = true,
                    extraActions = listOf(
                        ExtraAction(
                            viewId = "decor",
                            isGroup = true,
                            self = false,
                            childIndex = 0,
                            action = ViewAction.Inset(
                                spacingType = SpacingType.PADDING,
                                edge = InsetEdge.BOTTOM,
                                useSystemInsets = false,
                                customInset = 0
                            )
                        )
                    )
                ),
                "com.alipay.mobile.chatapp.ui.PersonalChatMsgActivity_" to PageConfig(
                    navigationColor = Color.WHITE,
                    windowBackgroundColor = Color.WHITE
                )
            ),
            general = GeneralConfig(
                PageConfig(edgeToEdge = true),
                exclusive = listOf(
                    "com.eg.android.AlipayGphone.AlipayLogin"
                )
            )
        )
    )
}
