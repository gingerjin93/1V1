// Copyright (c) 2022 NetEase, Inc. All rights reserved.
// Use of this source code is governed by a MIT license that can be
// found in the LICENSE file.

import NIMSDK
import UIKit

class MessageLocationModel: MessageContentModel {
  public var lat: Double?
  public var lng: Double?
  public var title: String?
  public var subTitle: String?
  required init(message: NIMMessage?) {
    super.init(message: message)
    type = .location
    if let locationObject = message?.messageObject as? NIMLocationObject {
      lat = locationObject.latitude
      lng = locationObject.longitude
      subTitle = locationObject.title
      title = message?.text
      contentSize = CGSize(width: 242, height: 140)
    }
    height = Float(contentSize.height + chat_content_margin) + fullNameHeight
  }
}
