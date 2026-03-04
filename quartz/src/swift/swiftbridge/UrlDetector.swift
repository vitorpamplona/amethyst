//
// Created by NullDev on 20/01/2026.
//

import Foundation

@objcMembers public class UrlDetector: NSObject {
    public func findURLs(text: String) -> [String] {
        var links = [String]()
        let detector = try! NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue)
        let matches = detector.matches(in: text, options: [], range: NSRange(location: 0, length: text.utf16.count))

        for match in matches {
            guard let range = Range(match.range, in: text) else { continue }
            let url = text[range]
            links.append(String(url))
        }

        return links
    }
}