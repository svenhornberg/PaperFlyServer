/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.fhb.paperfly.server.chat;

import java.util.Date;
import javax.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 *
 * @author Michael Koppen <michael.koppen@googlemail.com>
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = false)
//@NoArgsConstructor
//@AllArgsConstructor
public class Message {

	@Transient
	private String email;
	private MessageType type;
	private Date sendTime;
	@Transient
	private String room;
	private String body;

	public Message() {
	}

	public Message(String body) {
		this.body = body;
	}
}
