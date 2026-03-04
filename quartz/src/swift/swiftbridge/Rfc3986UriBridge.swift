//
// Created by NullDev on 31/12/2025.
//

import Foundation
import RFC_3986

@objcMembers public class Rfc3986UriBridge: NSObject {
    public func normalizeUrl(url: String) throws -> String {
        let uri = try RFC_3986.URI(url)
        let normalized = uri.normalized()
        return normalized.value
    }

    public func isUrlValid(url: String) -> Bool {
        return RFC_3986.isValidURI(url)
    }

    public func hostFromUri(url: String) throws -> String {
        let actualUri = try RFC_3986.URI(url)
        return actualUri.host!
    }
}
