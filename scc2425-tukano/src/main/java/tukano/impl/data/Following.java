package tukano.impl.data;

import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "following")
public class Following{

	@Id
    String id;
	
	String follower;
	
	String followee;

	Following() {}

	public Following(String follower, String followee) {
		super();
		this.id = follower + ":" + followee;
		this.follower = follower;
		this.followee = followee;
	}

	public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

	public String getFollower() {
		return follower;
	}

	public void setFollower(String follower) {
		this.follower = follower;
	}

	public String getFollowee() {
		return followee;
	}

	public void setFollowee(String followee) {
		this.followee = followee;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, followee, follower);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Following other = (Following) obj;
		return Objects.equals(followee, other.followee) && Objects.equals(follower, other.follower);
	}

	@Override
	public String toString() {
		return "Following [follower=" + follower + ", followee=" + followee + "]";
	}
	
	
}