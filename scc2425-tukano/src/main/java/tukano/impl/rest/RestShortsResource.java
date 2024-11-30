package tukano.impl.rest;

import java.util.List;

import jakarta.inject.Singleton;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.Users;
import tukano.api.rest.RestShorts;
import tukano.impl.JavaShorts;
import tukano.impl.JavaShortsNoCache;
import tukano.impl.JavaShortsNoSQL;
import tukano.impl.JavaShortsNoSQLNoCache;

@Singleton
public class RestShortsResource extends RestResource implements RestShorts {

	final Shorts impl;
	public RestShortsResource() {
		this.impl = JavaShorts.getInstance(); // Change here between: JavaShorts and JavaShortsNoSQL
	}		
	@Override
	public Short createShort(String userId, String password) {
		return super.resultOrThrow( impl.createShort(userId, password));
	}

	@Override
	public void deleteShort(String shortId, String password) {
		super.resultOrThrow( impl.deleteShort(shortId, password));
	}

	@Override
	public Short getShort(String shortId) {
		return super.resultOrThrow( impl.getShort(shortId));
	}
	@Override
	public List<String> getShorts(String userId) {
		return super.resultOrThrow( impl.getShorts(userId));
	}

	@Override
	public void follow(String userId1, String userId2, boolean isFollowing, String password) {
		super.resultOrThrow( impl.follow(userId1, userId2, isFollowing, password));
	}

	@Override
	public List<String> followers(String userId, String password) {
		return super.resultOrThrow( impl.followers(userId, password));
	}

	@Override
	public void like(String shortId, String userId, boolean isLiked, String password) {
		super.resultOrThrow( impl.like(shortId, userId, isLiked, password));
	}

	@Override
	public List<String> likes(String shortId, String password) {
		return super.resultOrThrow( impl.likes(shortId, password));
	}

	@Override
	public List<String> getFeed(String userId, String password) {
		return super.resultOrThrow( impl.getFeed(userId, password));
	}

	@Override
	public void deleteAllShorts(String userId, String password, String token) {
		super.resultOrThrow( impl.deleteAllShorts(userId, password, token));
	}	
}
