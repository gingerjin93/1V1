// Copyright (c) 2022 NetEase, Inc. All rights reserved.
// Use of this source code is governed by a MIT license that can be
// found in the LICENSE file.

import Foundation

// 国内服务器地址
let kApiHost: String = "https://yiyong.netease.im"

// 国内服务器地址
let kOverSeaApiHost: String = "https://yiyong-sg.netease.im"

// 数据收集
let kApiDataHost: String = "https://statistic.live.126.net"

// MARK: 海外环境与国内环境的切换可以在我的页面中进行修改

// 请填写您的appKey,国内环境请填写APP_KEY_MAINLAND，海外环境请填写APP_KEY_OVERSEA
let APP_KEY_MAINLAND: String = "your mainland appKey" // 国内用户填写AppKey

let APP_SECRET_MAINLAND: String = "your mainland appSecret" // 国内用户填写AppSecret

let APP_KEY_OVERSEA: String = "your oversea appKey" // 海外用户填写AppKey

let APP_SECRET_OVERSEA: String = "your oversea appSecret" // 海外用户填写AppSecret

let kPushCerName: String = "push notification name"

let kAppAMapKey: String = "A Map key"

// 获取userUuid和对应的userToken，请参考https://doc.yunxin.163.com/neroom/docs/TY1NzM5MjQ?platform=server

// MARK: 单租户模式下需要填写以下内容
// userUuid
var userUuid: String = ""
// userToken
var userToken: String = ""

// imToken
var imToken: String = ""

// MARK: 以下内容选填
//
// 以下内容选填

var userName: String = ""

var icon: String = ""
