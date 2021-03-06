/*
 * Copyright (C) 2016 Cognifide Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.knotx.fragments;

import io.knotx.dataobjects.Fragment;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

public final class FragmentContentExtractor {

  private FragmentContentExtractor() {
    //util class
  }

  /**
   * Extracts pure markup from the given {@link Fragment}'s content removing a script tag with
   * Knot.x specific data.
   *
   * @param fragment that content will be unwrapped.
   * @return markup without Knotx data processing instructions.
   */
  public static String unwrapContent(Fragment fragment) {
    if (fragment == null) {
      return null;
    }

    String content = fragment.content();
    return fragment.isRaw() ? content
        : content.substring(content.indexOf('>') + 1, content.lastIndexOf('<'));
  }

  /**
   * Extracts pure markup from the given {@link Fragment}'s content removing a script tag with
   * Knot.x specific data.
   *
   * @param fragment that content will be unwrapped.
   * @return markup without Knotx data processing instructions in a form of jsoup {@link Document}.
   */
  public static Document unwrapDocument(Fragment fragment) {
    if (fragment == null) {
      return null;
    }
    return unwrapDocument(fragment.content());
  }

  /**
   * Extracts pure markup from the given markup.
   *
   * @param content markup that contains Knotx processing instructions.
   * @return markup without Knotx data processing instructions in a form of jsoup {@link Document}.
   */
  public static Document unwrapDocument(String content) {
    Element scriptTag = Jsoup.parseBodyFragment(content).body().child(0);
    return Jsoup.parse(scriptTag.unwrap().toString(), "UTF-8", Parser.xmlParser());
  }
}
