package com.nixmash.springdata.mvc.controller;

import com.nixmash.springdata.jpa.common.ApplicationSettings;
import com.nixmash.springdata.jpa.dto.TagDTO;
import com.nixmash.springdata.jpa.enums.PostDisplayType;
import com.nixmash.springdata.jpa.model.CurrentUser;
import com.nixmash.springdata.jpa.model.Post;
import com.nixmash.springdata.jpa.service.PostService;
import com.nixmash.springdata.jpa.utils.Pair;
import com.nixmash.springdata.jpa.utils.PostUtils;
import com.nixmash.springdata.mail.service.TemplateService;
import com.nixmash.springdata.mvc.annotations.JsonRequestMapping;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Slice;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;

import static com.nixmash.springdata.mvc.controller.PostsController.POST_PAGING_SIZE;
import static com.nixmash.springdata.mvc.controller.PostsController.TITLE_PAGING_SIZE;


@RestController
@JsonRequestMapping(value = "/json/posts")
public class PostsRestController {

    private static final Logger logger = LoggerFactory.getLogger(PostsRestController.class);
    private static final String TITLE_TEMPLATE = "title";
    private static final String SESSION_ATTRIBUTE_POSTS = "posts";
    private static final String SESSION_ATTRIBUTE_TAGPOSTTITLES = "tagposttitles";
    private static final String SESSION_ATTRIBUTE_POSTTITLES = "posttitles";
    private static final String SESSION_ATTRIBUTE_TAGGEDPOSTS = "taggedposts";
    private static final String SESSION_ATTRIBUTE_LIKEDPOSTS = "likedposts";

    private PostService postService;
    private TemplateService templateService;
    private ApplicationSettings applicationSettings;

    private int minTagCount = 0;
    private int maxTagCount = 0;

    @Autowired
    public PostsRestController(PostService postService, TemplateService templateService, ApplicationSettings applicationSettings) {
        this.postService = postService;
        this.templateService = templateService;
        this.applicationSettings = applicationSettings;
    }

    // region Post Titles

    @RequestMapping(value = "/titles/page/{pageNumber}", produces = "text/html;charset=UTF-8")
    public String getPostTitles(@PathVariable Integer pageNumber, HttpServletRequest request, CurrentUser currentUser) {
        Slice<Post> posts = postService.getPublishedPosts(pageNumber, TITLE_PAGING_SIZE);
        String result = populatePostStream(posts.getContent(), currentUser, TITLE_TEMPLATE);
        WebUtils.setSessionAttribute(request, SESSION_ATTRIBUTE_POSTTITLES, posts.getContent());
        return result;
    }

    @RequestMapping(value = "/titles/more")
    public String getTitleHasNext(HttpServletRequest request) {
        return hasNext(request, SESSION_ATTRIBUTE_POSTTITLES, TITLE_PAGING_SIZE);
    }

    // endregion

    // region Posts by Tag

    @RequestMapping(value = "/titles/tag/{tagid}/page/{pageNumber}",
            produces = "text/html;charset=UTF-8")
    public String getPostTitlesByTagId(@PathVariable long tagid,
                                       @PathVariable int pageNumber,
                                       HttpServletRequest request,
                                       CurrentUser currentUser) {
        Slice<Post> posts = postService.getPostsByTagId(tagid, pageNumber, TITLE_PAGING_SIZE);
        String result = populatePostStream(posts.getContent(), currentUser, TITLE_TEMPLATE);
        WebUtils.setSessionAttribute(request, SESSION_ATTRIBUTE_TAGPOSTTITLES, posts.getContent());
        return result;
    }


    @RequestMapping(value = "/titles/tag/{tagid}/more")
    public String getTagTitlesHasNext(@PathVariable int tagid, HttpServletRequest request) {
        return hasNext(request, SESSION_ATTRIBUTE_TAGPOSTTITLES, TITLE_PAGING_SIZE);
    }

    // endregion

    // region Posts

    @RequestMapping(value = "/page/{pageNumber}", produces = "text/html;charset=UTF-8")
    public String getPosts(@PathVariable Integer pageNumber, HttpServletRequest request, CurrentUser currentUser) {
        Slice<Post> posts = postService.getPublishedPosts(pageNumber, POST_PAGING_SIZE);
        String result = populatePostStream(posts.getContent(), currentUser);
        WebUtils.setSessionAttribute(request, SESSION_ATTRIBUTE_POSTS, posts.getContent());
        return result;
    }


    @RequestMapping(value = "/more")
    public String getHasNext(HttpServletRequest request) {
        return hasNext(request, SESSION_ATTRIBUTE_POSTS, POST_PAGING_SIZE);
    }

    // endregion

    // region Posts by Tag

    @RequestMapping(value = "/tag/{tagid}/page/{pageNumber}",
            produces = "text/html;charset=UTF-8")
    public String getPostsByTagId(@PathVariable long tagid,
                                  @PathVariable int pageNumber,
                                  HttpServletRequest request,
                                  CurrentUser currentUser) {
        Slice<Post> posts = postService.getPostsByTagId(tagid, pageNumber, POST_PAGING_SIZE);
        String result = populatePostStream(posts.getContent(), currentUser);
        WebUtils.setSessionAttribute(request, SESSION_ATTRIBUTE_TAGGEDPOSTS, posts.getContent());
        return result;
    }

    @RequestMapping(value = "/tag/{tagid}/more")
    public String getHasNext(@PathVariable int tagid, HttpServletRequest request) {
        return hasNext(request, SESSION_ATTRIBUTE_TAGGEDPOSTS, POST_PAGING_SIZE);
    }

    // endregion

    // region Likes

    @RequestMapping(value = "/post/like/{postId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public int likePost(@PathVariable("postId") int postId, CurrentUser currentUser) {
        return postService.addPostLike(currentUser.getId(), postId);
    }

    @RequestMapping(value = "/likes/{userId}/page/{pageNumber}",
            produces = "text/html;charset=UTF-8")
    public String getPostsByLikes(@PathVariable long userId,
                                  @PathVariable int pageNumber,
                                  HttpServletRequest request,
                                  CurrentUser currentUser) {
        List<Post> posts = postService.getPagedLikedPosts(userId, pageNumber, POST_PAGING_SIZE);
        String result = populatePostStream(posts, currentUser);
        WebUtils.setSessionAttribute(request, SESSION_ATTRIBUTE_LIKEDPOSTS, posts);
        return result;
    }

    @RequestMapping(value = "/likes/{userId}/more")
    public String getHasNextLike(@PathVariable int userId, HttpServletRequest request) {
        return hasNext(request, SESSION_ATTRIBUTE_LIKEDPOSTS, POST_PAGING_SIZE);
    }

    // endregion

    // region Shared Utils

    @SuppressWarnings("unchecked")
    private String hasNext(HttpServletRequest request, String attribute, int pagingSize) {
        List<Post> posts = (List<Post>) WebUtils.getSessionAttribute(request, attribute);
        if (posts != null)
            return Boolean.toString(posts.size() >= pagingSize);
        else
            return "true";
    }

    private String populatePostStream(List<Post> posts, CurrentUser currentUser) {
        return populatePostStream(posts, currentUser, null);
    }

    private String populatePostStream(List<Post> posts, CurrentUser currentUser, String format) {
        String result = StringUtils.EMPTY;
        for (Post post : posts) {
            if (post.getDisplayType().equals(PostDisplayType.MULTIPHOTO_POST)) {
                post.setPostImages(postService.getPostImages(post.getPostId()));
            }
            post.setIsOwner(PostUtils.isPostOwner(currentUser, post.getUserId()));
            result += templateService.createPostHtml(post, format);
        }
        return result;
    }

    // endregion

    // region get Tags

    @RequestMapping(value = "/tags", produces = MediaType.APPLICATION_JSON_VALUE)
    public Set<TagDTO> getAllTagDTOs() {
        return postService.getTagDTOs();
    }

    @RequestMapping(value = "/tagvalues", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> getTagValues() {
        return postService.getTagValues();
    }


    @RequestMapping(value = "/tagcloud", produces = "text/html;charset=UTF-8")
    public String getTagCloud() {

        List<TagDTO> tags = postService.getTagCloud();
        maxTagCount = tags.stream().mapToInt(TagDTO::getTagCount).max().orElse(0);
        minTagCount = tags.stream().mapToInt(TagDTO::getTagCount).min().orElse(0);

        StringBuilder tagHtml = new StringBuilder();
        tagHtml.append("<ul class='taglist'>");
        for (TagDTO tag : tags) {
            tagHtml.append(tagHtml(tag));
        }
        tagHtml.append("</ul>");
        return tagHtml.toString();
    }

    private String tagHtml(TagDTO tag) {
        String tagPattern = "<li><a href='%s/posts/tag/%s' class='%s'>%s</a></li>";
        String cssClass = getCssTag(tag.getTagCount());
        String tagLowerCase = tag.getTagValue().toLowerCase();

        return String.format(tagPattern,
                applicationSettings.getBaseUrl(), tagLowerCase, cssClass, tag.getTagValue());
    }

    // endregion

    // region Tag Cloud

    private String getCssTag(int tagCount) {

        String cssClass = "smallTag";
        int diff = maxTagCount - minTagCount;
        int distribution = diff / 5;

        if (tagCount == maxTagCount)
            cssClass = "maxTag";
        else if (tagCount == minTagCount)
            cssClass = "minTag";
        else if (tagCount > (minTagCount + (distribution * 1.75)))
            cssClass = "largeTag";
        else if (tagCount > (minTagCount + distribution))
            cssClass = "mediumTag";

        return cssClass;
    }

    // endregion

    // region Key-Value Json

    //
    // --- demo for NixMash Post "Variations on JSON Key-Value Pairs in Spring MVC"  http://goo.gl/0hhnZg

    private String key = "key";
    private String value = "Json Key-Value Demo";

    /*
    *           Returns:  {  "key" : "Json Key-Value Demo"  }
     */
    @RequestMapping(value = "/map", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> returnMap() {
        Map<String, String> keyvalues = new HashMap<>();
        keyvalues.put(key, value);
        return keyvalues;
    }

    /*
    *           Returns:  {  "key" : "Json Key-Value Demo"  }
     */
    @RequestMapping(value = "/simpleentry")
    public SimpleEntry<String, String> returnSimpleEntry() {
        return new SimpleEntry<>(key, value);
    }

    /*
    *           Returns:  {  "key" : "Json Key-Value Demo"  }
     */
    @RequestMapping(value = "/singleton")
    public Map<String, String> returnSingletonMapFromCollection() {
        return Collections.singletonMap(key, value);
    }

    /*
    *           Returns:
    *           {
    *                    "key" : "key",
    *                     "value" : "Json Key-Value Demo"
    *           }
     */
    @RequestMapping(value = "/pair")
    public Pair<String, String> returnPair() {
        return new Pair<>(key, value);
    }

    // endregion

}
